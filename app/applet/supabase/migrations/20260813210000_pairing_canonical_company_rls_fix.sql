-- Migration: 20260813210000_pairing_canonical_company_rls_fix.sql
-- Fix for PHASE 3.7B-R19: Runtime Pairing + Canonical Company ID Forensic Trace

ALTER TABLE public.workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.connected_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cloud_records ENABLE ROW LEVEL SECURITY;

-- 1. Helper function: check if workspace exists (SECURITY DEFINER to avoid RLS recursion)
CREATE OR REPLACE FUNCTION public.workspace_exists(target_company_id TEXT)
RETURNS BOOLEAN
SECURITY DEFINER
SET search_path = public
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

-- 2. Helper function: check if caller's auth.uid() is a member of workspace
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
GRANT EXECUTE ON FUNCTION public.is_device_member_of_workspace(TEXT) TO anon, authenticated;

-- 3. Helper function: check if caller's auth.uid() is an active Mother Account of workspace
CREATE OR REPLACE FUNCTION public.is_mother_account_of_workspace(target_company_id TEXT)
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
        AND role = 'Mother Account'
        AND status = 'Active'
    );
END;
$$;
REVOKE ALL ON FUNCTION public.is_mother_account_of_workspace(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_mother_account_of_workspace(TEXT) TO anon, authenticated;

-- 4. Helper function: check if caller's auth.uid() is the creator of workspace
CREATE OR REPLACE FUNCTION public.is_workspace_creator(target_company_id TEXT)
RETURNS BOOLEAN
SECURITY DEFINER
SET search_path = public
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.workspaces 
        WHERE company_id = target_company_id 
        AND (creator_uid = (SELECT auth.uid()::text) OR creator_uid IS NULL)
    );
END;
$$;
REVOKE ALL ON FUNCTION public.is_workspace_creator(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_workspace_creator(TEXT) TO anon, authenticated;

-- =====================================================
-- 5. WORKSPACES POLICIES
-- =====================================================
DROP POLICY IF EXISTS "Public can view workspaces" ON public.workspaces;
DROP POLICY IF EXISTS "Users can view their own workspace" ON public.workspaces;
DROP POLICY IF EXISTS "Users can view workspaces" ON public.workspaces;
CREATE POLICY "Users can view workspaces" ON public.workspaces
FOR SELECT
TO authenticated, anon
USING (true);

DROP POLICY IF EXISTS "Authenticated users can insert workspaces" ON public.workspaces;
CREATE POLICY "Authenticated users can insert workspaces"
ON public.workspaces FOR INSERT
TO authenticated
WITH CHECK (
    (SELECT auth.uid()) IS NOT NULL
);

DROP POLICY IF EXISTS "Users can update their workspace" ON public.workspaces;
CREATE POLICY "Users can update their workspace"
ON public.workspaces FOR UPDATE
TO authenticated
USING (
    creator_uid = (SELECT auth.uid()::text) 
    OR public.is_mother_account_of_workspace(company_id)
)
WITH CHECK (
    creator_uid = (SELECT auth.uid()::text) 
    OR public.is_mother_account_of_workspace(company_id)
);

-- =====================================================
-- 6. CONNECTED_DEVICES POLICIES
-- =====================================================
DROP POLICY IF EXISTS "Users can view devices in their workspace" ON public.connected_devices;
CREATE POLICY "Users can view devices in their workspace"
ON public.connected_devices FOR SELECT
TO authenticated
USING (
    uid = (SELECT auth.uid()::text) 
    OR public.is_device_member_of_workspace(company_id)
);

DROP POLICY IF EXISTS "Users can insert their own device" ON public.connected_devices;
CREATE POLICY "Users can insert their own device"
ON public.connected_devices FOR INSERT
TO authenticated
WITH CHECK (
    (SELECT auth.uid()) IS NOT NULL 
    AND uid = (SELECT auth.uid()::text)
    AND (
        -- Branch 1: Mother Account creating active device row for workspace created by same auth user
        (role = 'Mother Account' AND status = 'Active' AND public.is_workspace_creator(company_id))
        OR 
        -- Branch 2: Device B creating its own Pending pairing request for an existing canonical workspace
        (role != 'Mother Account' AND status = 'Pending' AND public.workspace_exists(company_id))
    )
);

DROP POLICY IF EXISTS "Users can update their own device or Mother Account can update any" ON public.connected_devices;
CREATE POLICY "Users can update their own device or Mother Account can update any"
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

DROP POLICY IF EXISTS "Mother Account can delete devices" ON public.connected_devices;
CREATE POLICY "Mother Account can delete devices"
ON public.connected_devices FOR DELETE
TO authenticated
USING (
    public.is_mother_account_of_workspace(company_id)
);

-- =====================================================
-- 7. CLOUD_RECORDS POLICIES
-- =====================================================
DROP POLICY IF EXISTS "Members can insert cloud records" ON public.cloud_records;
CREATE POLICY "Members can insert cloud records"
ON public.cloud_records FOR INSERT
TO authenticated
WITH CHECK (
    public.is_device_member_of_workspace(company_id)
);

DROP POLICY IF EXISTS "Members can read cloud records" ON public.cloud_records;
CREATE POLICY "Members can read cloud records"
ON public.cloud_records FOR SELECT
TO authenticated
USING (
    public.is_device_member_of_workspace(company_id)
);

DROP POLICY IF EXISTS "Members can update cloud records" ON public.cloud_records;
CREATE POLICY "Members can update cloud records"
ON public.cloud_records FOR UPDATE
TO authenticated
USING (
    public.is_device_member_of_workspace(company_id)
)
WITH CHECK (
    public.is_device_member_of_workspace(company_id)
);

DROP POLICY IF EXISTS "Members can delete cloud records" ON public.cloud_records;
CREATE POLICY "Members can delete cloud records"
ON public.cloud_records FOR DELETE
TO authenticated
USING (
    public.is_device_member_of_workspace(company_id)
);

-- Grants
GRANT SELECT, INSERT, UPDATE, DELETE ON public.workspaces TO authenticated, anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.connected_devices TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.cloud_records TO authenticated;
