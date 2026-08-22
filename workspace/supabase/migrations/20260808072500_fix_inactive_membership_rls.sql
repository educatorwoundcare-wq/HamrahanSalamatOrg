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
        AND status IN ('Active', 'Pending')
    );
END;
$$;
