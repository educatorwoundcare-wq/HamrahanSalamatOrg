-- Fix Workspace Bootstrap + Cloud Records RLS

BEGIN;

-- =====================================================
-- 1. Fix user signup trigger
-- =====================================================

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    new_company_id text;
BEGIN

    new_company_id := gen_random_uuid()::text;

    INSERT INTO public.workspaces
    (
        company_id,
        sync_code,
        center_name,
        created_timestamp,
        creator_uid
    )
    VALUES
    (
        new_company_id,
        substring(md5(random()::text) from 1 for 8),
        'New Office',
        extract(epoch from now())::bigint,
        NEW.id::text
    );


    INSERT INTO public.user_profiles
    (
        id,
        tenant_id,
        role
    )
    VALUES
    (
        NEW.id,
        new_company_id::uuid,
        'ADMIN'
    )
    ON CONFLICT (id)
    DO NOTHING;


    RETURN NEW;

END;
$$;


-- =====================================================
-- 2. Enable RLS on cloud_records
-- =====================================================

ALTER TABLE public.cloud_records ENABLE ROW LEVEL SECURITY;


-- =====================================================
-- 3. Insert Policy
-- =====================================================

CREATE POLICY "Members can insert cloud records"
ON public.cloud_records
FOR INSERT
TO authenticated
WITH CHECK
(
    is_device_member_of_workspace(company_id)
);


-- =====================================================
-- 4. Select Policy
-- =====================================================

CREATE POLICY "Members can read cloud records"
ON public.cloud_records
FOR SELECT
TO authenticated
USING
(
    is_device_member_of_workspace(company_id)
);


-- =====================================================
-- 5. Update Policy
-- =====================================================

CREATE POLICY "Members can update cloud records"
ON public.cloud_records
FOR UPDATE
TO authenticated
USING
(
    is_device_member_of_workspace(company_id)
)
WITH CHECK
(
    is_device_member_of_workspace(company_id)
);


-- =====================================================
-- 6. Delete Policy
-- =====================================================

CREATE POLICY "Members can delete cloud records"
ON public.cloud_records
FOR DELETE
TO authenticated
USING
(
    is_device_member_of_workspace(company_id)
);


COMMIT;
