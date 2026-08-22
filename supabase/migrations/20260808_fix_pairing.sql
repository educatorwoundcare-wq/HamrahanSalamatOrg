-- 1. Create a function to securely resolve a workspace by sync code
CREATE OR REPLACE FUNCTION public.resolve_workspace_by_sync_code(p_sync_code TEXT)
RETURNS TABLE (
    company_id TEXT,
    center_name TEXT,
    created_timestamp BIGINT
)
SECURITY DEFINER
SET search_path = public
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT w.company_id, w.center_name, w.created_timestamp
    FROM public.workspaces w
    WHERE w.sync_code = p_sync_code
    LIMIT 1;
END;
$$;

REVOKE ALL ON FUNCTION public.resolve_workspace_by_sync_code(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_workspace_by_sync_code(TEXT) TO anon, authenticated;

-- 2. Ensure workspaces table has a unique constraint on sync_code
ALTER TABLE public.workspaces DROP CONSTRAINT IF EXISTS workspaces_sync_code_key;
ALTER TABLE public.workspaces ADD CONSTRAINT workspaces_sync_code_key UNIQUE (sync_code);

-- 3. Ensure connected_devices has unique constraints to prevent duplicate pairings
ALTER TABLE public.connected_devices DROP CONSTRAINT IF EXISTS connected_devices_company_device_key;
ALTER TABLE public.connected_devices ADD CONSTRAINT connected_devices_company_device_key UNIQUE (company_id, device_id);

-- 4. Enable RLS on workspaces and connected_devices
ALTER TABLE public.workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.connected_devices ENABLE ROW LEVEL SECURITY;

-- 5. Helper function for RLS to prevent recursion
CREATE OR REPLACE FUNCTION public.is_device_member_of_workspace(target_company_id TEXT)
RETURNS BOOLEAN
SECURITY DEFINER
SET search_path = public
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.connected_devices 
        WHERE company_id = target_company_id 
        AND uid = (SELECT auth.uid()::text)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.is_device_member_of_workspace(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_device_member_of_workspace(TEXT) TO authenticated;

-- 6. Track Workspace Creator for secure Primary Device insertion
ALTER TABLE public.workspaces ADD COLUMN IF NOT EXISTS creator_uid TEXT;

CREATE OR REPLACE FUNCTION public.set_workspace_creator() RETURNS TRIGGER AS $$
BEGIN
    NEW.creator_uid = (SELECT auth.uid()::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS set_workspace_creator_trigger ON public.workspaces;
CREATE TRIGGER set_workspace_creator_trigger
BEFORE INSERT ON public.workspaces
FOR EACH ROW EXECUTE FUNCTION public.set_workspace_creator();

-- 7. Policies for workspaces
DROP POLICY IF EXISTS "Public can view workspaces" ON public.workspaces;
DROP POLICY IF EXISTS "Users can view their own workspace" ON public.workspaces;
CREATE POLICY "Users can view their own workspace" ON public.workspaces FOR SELECT 
USING (public.is_device_member_of_workspace(company_id) OR creator_uid = (SELECT auth.uid()::text));

DROP POLICY IF EXISTS "Authenticated users can insert workspaces" ON public.workspaces;
CREATE POLICY "Authenticated users can insert workspaces"
ON public.workspaces FOR INSERT
WITH CHECK (auth.uid() IS NOT NULL); -- Requires auth, trigger handles creator_uid

DROP POLICY IF EXISTS "Users can update their workspace" ON public.workspaces;
CREATE POLICY "Users can update their workspace"
ON public.workspaces FOR UPDATE
USING (
    EXISTS (
        SELECT 1 FROM public.connected_devices cd 
        WHERE cd.company_id = workspaces.company_id 
        AND cd.uid = (SELECT auth.uid()::text) 
        AND cd.role = 'Mother Account'
        AND cd.status = 'Active'
    )
) WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.connected_devices cd 
        WHERE cd.company_id = workspaces.company_id 
        AND cd.uid = (SELECT auth.uid()::text) 
        AND cd.role = 'Mother Account'
        AND cd.status = 'Active'
    )
);

-- 8. Policies for connected_devices
DROP POLICY IF EXISTS "Users can view devices in their workspace" ON public.connected_devices;
CREATE POLICY "Users can view devices in their workspace"
ON public.connected_devices FOR SELECT
USING (public.is_device_member_of_workspace(company_id));

DROP POLICY IF EXISTS "Users can insert their own device" ON public.connected_devices;
CREATE POLICY "Users can insert their own device"
ON public.connected_devices FOR INSERT
WITH CHECK (
    uid = (SELECT auth.uid()::text) AND (
        (role = 'Mother Account' AND status = 'Active' AND EXISTS (SELECT 1 FROM public.workspaces WHERE company_id = connected_devices.company_id AND creator_uid = (SELECT auth.uid()::text)))
        OR 
        (role != 'Mother Account' AND status = 'Pending')
    )
);

DROP POLICY IF EXISTS "Users can update their own device or Mother Account can update any" ON public.connected_devices;
CREATE POLICY "Users can update their own device or Mother Account can update any"
ON public.connected_devices FOR UPDATE
USING (
    uid = (SELECT auth.uid()::text) OR 
    EXISTS (
        SELECT 1 FROM public.connected_devices cd 
        WHERE cd.company_id = connected_devices.company_id 
        AND cd.uid = (SELECT auth.uid()::text) 
        AND cd.role = 'Mother Account'
        AND cd.status = 'Active'
    )
);

-- 9. Trigger to prevent Self-Elevation (Role/Status/Tenant changes) by non-Admins
CREATE OR REPLACE FUNCTION public.prevent_device_self_elevation() RETURNS TRIGGER AS $$
BEGIN
    -- Prevent moving across workspaces or assigning to a different auth user
    IF NEW.company_id != OLD.company_id OR NEW.uid != OLD.uid THEN
        RAISE EXCEPTION 'Cannot change company_id or uid';
    END IF;

    -- If the user is NOT a Mother Account for this company, prevent role or status changes
    IF NOT EXISTS (
        SELECT 1 FROM public.connected_devices 
        WHERE company_id = NEW.company_id 
        AND uid = (SELECT auth.uid()::text) 
        AND role = 'Mother Account' 
        AND status = 'Active'
    ) THEN
        -- Force the NEW row to keep the OLD role and status
        NEW.role = OLD.role;
        NEW.status = OLD.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS prevent_device_self_elevation_trigger ON public.connected_devices;
CREATE TRIGGER prevent_device_self_elevation_trigger
BEFORE UPDATE ON public.connected_devices
FOR EACH ROW EXECUTE FUNCTION public.prevent_device_self_elevation();

DROP POLICY IF EXISTS "Mother Account can delete devices" ON public.connected_devices;
CREATE POLICY "Mother Account can delete devices"
ON public.connected_devices FOR DELETE
USING (
    EXISTS (
        SELECT 1 FROM public.connected_devices cd 
        WHERE cd.company_id = connected_devices.company_id 
        AND cd.uid = (SELECT auth.uid()::text) 
        AND cd.role = 'Mother Account'
        AND cd.status = 'Active'
    )
);
