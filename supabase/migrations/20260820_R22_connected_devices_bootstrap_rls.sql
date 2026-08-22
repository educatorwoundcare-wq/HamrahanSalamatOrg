-- Migration: 20260820_R22_connected_devices_bootstrap_rls.sql
-- Description: Deterministic bootstrap RLS for connected_devices, enabling initial workspace creator Mother Account bootstrap and strict privilege separation.

ALTER TABLE public.connected_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workspaces ENABLE ROW LEVEL SECURITY;

-- 1. Helper function: check if caller's auth.uid() is the creator of workspace
CREATE OR REPLACE FUNCTION public.is_workspace_creator(target_company_id TEXT)
RETURNS BOOLEAN
SECURITY DEFINER
SET search_path = public, auth
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.workspaces 
        WHERE company_id = target_company_id 
        AND creator_uid = (SELECT auth.uid()::text)
    );
END;
$$;
REVOKE ALL ON FUNCTION public.is_workspace_creator(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_workspace_creator(TEXT) TO anon, authenticated;

-- 2. Helper function: check if workspace exists
CREATE OR REPLACE FUNCTION public.workspace_exists(target_company_id TEXT)
RETURNS BOOLEAN
SECURITY DEFINER
SET search_path = public, auth
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.workspaces 
        WHERE company_id = target_company_id
    );
END;
$$;
REVOKE ALL ON FUNCTION public.workspace_exists(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.workspace_exists(TEXT) TO anon, authenticated;

-- 3. Helper function: check if caller is active Mother Account of workspace
CREATE OR REPLACE FUNCTION public.is_mother_account_of_workspace(target_company_id TEXT)
RETURNS BOOLEAN
SECURITY DEFINER
SET search_path = public, auth
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.connected_devices 
        WHERE company_id = target_company_id 
        AND uid = (SELECT auth.uid()::text)
        AND role = 'Mother Account'
        AND status = 'Active'
    );
END;
$$;
REVOKE ALL ON FUNCTION public.is_mother_account_of_workspace(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_mother_account_of_workspace(TEXT) TO anon, authenticated;

-- 4. Helper function: check if caller is an enrolled device member
CREATE OR REPLACE FUNCTION public.is_device_member_of_workspace(target_company_id TEXT)
RETURNS BOOLEAN
SECURITY DEFINER
SET search_path = public, auth
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
GRANT EXECUTE ON FUNCTION public.is_device_member_of_workspace(TEXT) TO anon, authenticated;

-- 5. Drop conflicting legacy policies on connected_devices
DROP POLICY IF EXISTS "Users can insert their own device" ON public.connected_devices;
DROP POLICY IF EXISTS "Users can update their own device or Mother Account can update any" ON public.connected_devices;
DROP POLICY IF EXISTS "Users can update their own device or Mother Account can update" ON public.connected_devices;
DROP POLICY IF EXISTS "Users can view devices in their workspace" ON public.connected_devices;
DROP POLICY IF EXISTS "Mother Account can delete devices" ON public.connected_devices;

-- 6. SELECT Policy
CREATE POLICY "Users can view devices in their workspace"
ON public.connected_devices FOR SELECT
TO authenticated
USING (
    uid = (SELECT auth.uid()::text) 
    OR public.is_device_member_of_workspace(company_id)
    OR public.is_workspace_creator(company_id)
);

-- 7. INSERT Policy (Deterministic Bootstrap + Pending Registration)
CREATE POLICY "Users can insert their own device"
ON public.connected_devices FOR INSERT
TO authenticated
WITH CHECK (
    (SELECT auth.uid()) IS NOT NULL 
    AND uid = (SELECT auth.uid()::text)
    AND (
        -- PATH 1: WORKSPACE CREATOR BOOTSTRAP
        (
            status = 'Active' 
            AND role = 'Mother Account' 
            AND public.is_workspace_creator(company_id)
        )
        OR 
        -- PATH 2: NORMAL DEVICE REQUEST
        (
            status = 'Pending' 
            AND role <> 'Mother Account' 
            AND public.workspace_exists(company_id)
        )
    )
);

-- 8. Trigger to prevent normal users from modifying security fields on UPDATE
CREATE OR REPLACE FUNCTION public.enforce_connected_device_security_fields()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
    -- If caller is the active Mother Account of the workspace, allow authorization updates
    IF public.is_mother_account_of_workspace(OLD.company_id) THEN
        RETURN NEW;
    END IF;

    -- For normal device owners updating their own record:
    -- Security fields (company_id, uid, status, role, requested_role) MUST remain immutable
    IF NEW.uid <> OLD.uid 
       OR NEW.company_id <> OLD.company_id 
       OR NEW.status <> OLD.status 
       OR NEW.role <> OLD.role 
       OR (NEW.requested_role IS DISTINCT FROM OLD.requested_role) THEN
        RAISE EXCEPTION 'Unauthorized: Non-Mother accounts cannot modify security fields on connected_devices';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enforce_connected_device_security ON public.connected_devices;
CREATE TRIGGER trg_enforce_connected_device_security
BEFORE UPDATE ON public.connected_devices
FOR EACH ROW
EXECUTE FUNCTION public.enforce_connected_device_security_fields();

-- 9. UPDATE Policy
CREATE POLICY "Users can update their own device or Mother Account can update"
ON public.connected_devices FOR UPDATE
TO authenticated
USING (
    uid = (SELECT auth.uid()::text) 
    OR public.is_mother_account_of_workspace(company_id)
)
WITH CHECK (
    uid = (SELECT auth.uid()::text) 
    OR public.is_mother_account_of_workspace(company_id)
);

-- 10. DELETE Policy
CREATE POLICY "Mother Account can delete devices"
ON public.connected_devices FOR DELETE
TO authenticated
USING (
    public.is_mother_account_of_workspace(company_id)
);

-- 11. Explicit Grants
GRANT SELECT, INSERT, UPDATE, DELETE ON public.connected_devices TO authenticated;
