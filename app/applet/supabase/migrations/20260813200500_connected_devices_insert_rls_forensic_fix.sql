-- Migration: 20260813200500_connected_devices_insert_rls_forensic_fix.sql
-- Fix for PHASE 3.7B-R18: CONNECTED_DEVICES INSERT RLS FORENSIC FIX

ALTER TABLE public.connected_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workspaces ENABLE ROW LEVEL SECURITY;

-- 1. Helper function: check if workspace exists (SECURITY DEFINER to avoid RLS recursion/blocking on workspaces table during device registration)
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

-- 5. SELECT Policy
DROP POLICY IF EXISTS "Users can view devices in their workspace" ON public.connected_devices;
CREATE POLICY "Users can view devices in their workspace"
ON public.connected_devices FOR SELECT
TO authenticated
USING (
    uid = (SELECT auth.uid()::text) 
    OR public.is_device_member_of_workspace(company_id)
);

-- 6. INSERT Policy
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

-- 7. UPDATE Policy
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

-- 8. DELETE Policy
DROP POLICY IF EXISTS "Mother Account can delete devices" ON public.connected_devices;
CREATE POLICY "Mother Account can delete devices"
ON public.connected_devices FOR DELETE
TO authenticated
USING (
    public.is_mother_account_of_workspace(company_id)
);

-- 9. Explicit Grants
GRANT SELECT, INSERT, UPDATE, DELETE ON public.connected_devices TO authenticated;
