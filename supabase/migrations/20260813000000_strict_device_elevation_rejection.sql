-- Fix for PHASE 3.7B-R13.3: Strict Server Rejection for Unauthorized Device Elevation
CREATE OR REPLACE FUNCTION public.prevent_device_self_elevation() 
RETURNS TRIGGER AS $$
BEGIN
    -- Prevent moving across workspaces or assigning to a different auth user
    IF NEW.company_id != OLD.company_id OR NEW.uid != OLD.uid THEN
        RAISE EXCEPTION 'Cannot change company_id or uid';
    END IF;

    -- If the user is NOT an Active Mother Account for this company, prevent role or status changes
    IF NOT EXISTS (
        SELECT 1 FROM public.connected_devices 
        WHERE company_id = NEW.company_id 
        AND uid = (SELECT auth.uid()::text) 
        AND role = 'Mother Account' 
        AND status = 'Active'
    ) THEN
        IF NEW.role != OLD.role OR NEW.status != OLD.status THEN
            RAISE EXCEPTION 'Unauthorized device authorization change';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
