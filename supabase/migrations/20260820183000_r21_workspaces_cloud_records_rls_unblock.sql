-- Migration: 20260820183000_r21_workspaces_cloud_records_rls_unblock.sql
-- Description: Unblocks canonical workspace UPSERT and cloud_records synchronization for active members under strict RLS isolation.

CREATE OR REPLACE FUNCTION public.is_active_workspace_member(p_company_id text)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path TO 'public'
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.connected_devices cd
        WHERE cd.company_id = p_company_id
          AND cd.uid = (SELECT auth.uid()::text)
          AND cd.status = 'Active'
    );
$$;

CREATE OR REPLACE FUNCTION public.is_mother_account_of_workspace(target_company_id text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
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

CREATE OR REPLACE FUNCTION public.is_device_member_of_workspace(target_company_id text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.connected_devices
        WHERE company_id = target_company_id
        AND uid = (SELECT auth.uid()::text)
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.is_active_workspace_member(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_mother_account_of_workspace(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_device_member_of_workspace(text) TO authenticated;

-- ============================================================================
-- Workspaces RLS Policies
-- ============================================================================
ALTER TABLE public.workspaces ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Authenticated users can insert workspaces" ON public.workspaces;
DROP POLICY IF EXISTS "Users can update their workspace" ON public.workspaces;
DROP POLICY IF EXISTS "Users can view workspaces" ON public.workspaces;
DROP POLICY IF EXISTS "is_device_member_of_workspace_select" ON public.workspaces;

CREATE POLICY "Authenticated users can insert workspaces"
ON public.workspaces
FOR INSERT
TO authenticated
WITH CHECK (
    auth.uid() IS NOT NULL
    AND creator_uid = (SELECT auth.uid()::text)
);

CREATE POLICY "Users can update their workspace"
ON public.workspaces
FOR UPDATE
TO authenticated
USING (
    creator_uid = (SELECT auth.uid()::text)
    OR is_mother_account_of_workspace(company_id)
)
WITH CHECK (
    creator_uid = (SELECT auth.uid()::text)
    OR is_mother_account_of_workspace(company_id)
);

CREATE POLICY "Users can view workspaces"
ON public.workspaces
FOR SELECT
TO authenticated
USING (
    creator_uid = (SELECT auth.uid()::text)
    OR is_active_workspace_member(company_id)
    OR is_device_member_of_workspace(company_id)
);

-- ============================================================================
-- Cloud Records RLS Policies
-- ============================================================================
ALTER TABLE public.cloud_records ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Members can delete cloud records" ON public.cloud_records;
DROP POLICY IF EXISTS "Members can insert cloud records" ON public.cloud_records;
DROP POLICY IF EXISTS "Members can read cloud records" ON public.cloud_records;
DROP POLICY IF EXISTS "Members can update cloud records" ON public.cloud_records;
DROP POLICY IF EXISTS "workspace members can insert cloud records" ON public.cloud_records;
DROP POLICY IF EXISTS "workspace members can update cloud records" ON public.cloud_records;
DROP POLICY IF EXISTS "workspace members can view cloud records" ON public.cloud_records;
DROP POLICY IF EXISTS "Active members can select cloud records" ON public.cloud_records;
DROP POLICY IF EXISTS "Active members can insert cloud records" ON public.cloud_records;
DROP POLICY IF EXISTS "Active members can update cloud records" ON public.cloud_records;
DROP POLICY IF EXISTS "Active members can delete cloud records" ON public.cloud_records;

CREATE POLICY "Active members can select cloud records"
ON public.cloud_records
FOR SELECT
TO authenticated
USING (
    is_active_workspace_member(company_id)
);

CREATE POLICY "Active members can insert cloud records"
ON public.cloud_records
FOR INSERT
TO authenticated
WITH CHECK (
    is_active_workspace_member(company_id)
);

CREATE POLICY "Active members can update cloud records"
ON public.cloud_records
FOR UPDATE
TO authenticated
USING (
    is_active_workspace_member(company_id)
)
WITH CHECK (
    is_active_workspace_member(company_id)
);

CREATE POLICY "Active members can delete cloud records"
ON public.cloud_records
FOR DELETE
TO authenticated
USING (
    is_active_workspace_member(company_id)
);
