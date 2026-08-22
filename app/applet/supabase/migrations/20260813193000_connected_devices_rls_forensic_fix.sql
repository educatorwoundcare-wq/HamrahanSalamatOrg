-- Migration: 20260813193000_connected_devices_rls_forensic_fix.sql
-- Fix for PHASE 3.7B-R17: connected_devices RLS policy forensic fix

ALTER TABLE public.connected_devices ENABLE ROW LEVEL SECURITY;

-- 1. Helper function: check if caller's auth.uid() is a member of workspace
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

-- 2. Helper function: check if caller's auth.uid() is an active Mother Account of workspace
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

-- 3. SELECT Policy
DROP POLICY IF EXISTS "Users can view devices in their workspace" ON public.connected_devices;
CREATE POLICY "Users can view devices in their workspace"
ON public.connected_devices FOR SELECT
USING (
    uid = (SELECT auth.uid()::text) 
    OR public.is_device_member_of_workspace(company_id)
);

-- 4. INSERT Policy
DROP POLICY IF EXISTS "Users can insert their own device" ON public.connected_devices;
CREATE POLICY "Users can insert their own device"
ON public.connected_devices FOR INSERT
WITH CHECK (
    auth.uid() IS NOT NULL 
    AND uid = (SELECT auth.uid()::text)
    AND (
        (role = 'Mother Account' AND status = 'Active' AND EXISTS (SELECT 1 FROM public.workspaces WHERE company_id = connected_devices.company_id AND (creator_uid = (SELECT auth.uid()::text) OR creator_uid IS NULL)))
        OR 
        (role != 'Mother Account' AND status = 'Pending' AND EXISTS (SELECT 1 FROM public.workspaces WHERE company_id = connected_devices.company_id))
    )
);

-- 5. UPDATE Policy
DROP POLICY IF EXISTS "Users can update their own device or Mother Account can update any" ON public.connected_devices;
CREATE POLICY "Users can update their own device or Mother Account can update any"
ON public.connected_devices FOR UPDATE
USING (
    uid = (SELECT auth.uid()::text) 
    OR public.is_mother_account_of_workspace(company_id)
);

-- 6. DELETE Policy
DROP POLICY IF EXISTS "Mother Account can delete devices" ON public.connected_devices;
CREATE POLICY "Mother Account can delete devices"
ON public.connected_devices FOR DELETE
USING (
    public.is_mother_account_of_workspace(company_id)
);
