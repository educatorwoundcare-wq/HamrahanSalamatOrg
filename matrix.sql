-- Setup clean state
DELETE FROM public.connected_devices WHERE company_id IN ('R133_COMP_A', 'R133_COMP_B');
DELETE FROM public.workspaces WHERE company_id IN ('R133_COMP_A', 'R133_COMP_B');

INSERT INTO public.workspaces (company_id, center_name, creator_uid, sync_code) VALUES ('R133_COMP_A', 'Center A', '11111111-1111-1111-1111-111111111111', 'SYNC_R133_A');
INSERT INTO public.workspaces (company_id, center_name, creator_uid, sync_code) VALUES ('R133_COMP_B', 'Center B', '22222222-2222-2222-2222-222222222222', 'SYNC_R133_B');

INSERT INTO public.connected_devices (company_id, device_id, uid, role, status) VALUES ('R133_COMP_A', 'DEV_MOTHER_A', '11111111-1111-1111-1111-111111111111', 'Mother Account', 'Active');
INSERT INTO public.connected_devices (company_id, device_id, uid, role, status) VALUES ('R133_COMP_B', 'DEV_MOTHER_B', '22222222-2222-2222-2222-222222222222', 'Mother Account', 'Active');
INSERT INTO public.connected_devices (company_id, device_id, uid, role, status) VALUES ('R133_COMP_A', 'DEV_STAFF_A', '33333333-3333-3333-3333-333333333333', 'Staff', 'Pending');
INSERT INTO public.connected_devices (company_id, device_id, uid, role, status) VALUES ('R133_COMP_B', 'DEV_STAFF_B', '44444444-4444-4444-4444-444444444444', 'Staff', 'Pending');

DO $$
DECLARE
    rec RECORD;
    err_text TEXT;
BEGIN
    -- TEST A: Mother Pending -> Active
    SET LOCAL role = authenticated;
    PERFORM set_config('request.jwt.claims', '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
    UPDATE public.connected_devices SET status = 'Active' WHERE device_id = 'DEV_STAFF_A';
    SELECT status INTO rec FROM public.connected_devices WHERE device_id = 'DEV_STAFF_A';
    IF rec.status != 'Active' THEN RAISE EXCEPTION 'TEST A FAILED'; END IF;

    -- Reset
    UPDATE public.connected_devices SET status = 'Pending' WHERE device_id = 'DEV_STAFF_A';

    -- TEST B: Mother Pending -> Rejected
    UPDATE public.connected_devices SET status = 'Rejected' WHERE device_id = 'DEV_STAFF_A';
    SELECT status INTO rec FROM public.connected_devices WHERE device_id = 'DEV_STAFF_A';
    IF rec.status != 'Rejected' THEN RAISE EXCEPTION 'TEST B FAILED'; END IF;

    -- Reset
    UPDATE public.connected_devices SET status = 'Pending' WHERE device_id = 'DEV_STAFF_A';

    -- TEST C: Staff Pending -> Active (Expect Exception)
    PERFORM set_config('request.jwt.claims', '{"sub": "33333333-3333-3333-3333-333333333333", "role": "authenticated"}', true);
    BEGIN
        UPDATE public.connected_devices SET status = 'Active' WHERE device_id = 'DEV_STAFF_A';
        RAISE EXCEPTION 'TEST C FAILED: Allowed unauthorized update';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Unauthorized device authorization change%' THEN RAISE EXCEPTION 'TEST C FAILED: Wrong err %', SQLERRM; END IF;
    END;

    -- TEST D: Staff Pending -> Rejected (Expect Exception)
    BEGIN
        UPDATE public.connected_devices SET status = 'Rejected' WHERE device_id = 'DEV_STAFF_A';
        RAISE EXCEPTION 'TEST D FAILED: Allowed unauthorized update';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Unauthorized device authorization change%' THEN RAISE EXCEPTION 'TEST D FAILED: Wrong err %', SQLERRM; END IF;
    END;

    -- Set to Active by Mother
    PERFORM set_config('request.jwt.claims', '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
    UPDATE public.connected_devices SET status = 'Active' WHERE device_id = 'DEV_STAFF_A';

    -- TEST E: Staff Active -> Pending (Expect Exception)
    PERFORM set_config('request.jwt.claims', '{"sub": "33333333-3333-3333-3333-333333333333", "role": "authenticated"}', true);
    BEGIN
        UPDATE public.connected_devices SET status = 'Pending' WHERE device_id = 'DEV_STAFF_A';
        RAISE EXCEPTION 'TEST E FAILED: Allowed unauthorized update';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Unauthorized device authorization change%' THEN RAISE EXCEPTION 'TEST E FAILED: Wrong err %', SQLERRM; END IF;
    END;

    -- TEST F: Staff Active -> Rejected (Expect Exception)
    BEGIN
        UPDATE public.connected_devices SET status = 'Rejected' WHERE device_id = 'DEV_STAFF_A';
        RAISE EXCEPTION 'TEST F FAILED: Allowed unauthorized update';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Unauthorized device authorization change%' THEN RAISE EXCEPTION 'TEST F FAILED: Wrong err %', SQLERRM; END IF;
    END;

    -- Reset to Pending
    PERFORM set_config('request.jwt.claims', '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
    UPDATE public.connected_devices SET status = 'Pending' WHERE device_id = 'DEV_STAFF_A';

    -- TEST G: Staff role -> Mother Account (Expect Exception)
    PERFORM set_config('request.jwt.claims', '{"sub": "33333333-3333-3333-3333-333333333333", "role": "authenticated"}', true);
    BEGIN
        UPDATE public.connected_devices SET role = 'Mother Account' WHERE device_id = 'DEV_STAFF_A';
        RAISE EXCEPTION 'TEST G FAILED: Allowed unauthorized role change';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Unauthorized device authorization change%' THEN RAISE EXCEPTION 'TEST G FAILED: Wrong err %', SQLERRM; END IF;
    END;

    -- TEST H: Staff company_id mutation (Expect Exception)
    BEGIN
        UPDATE public.connected_devices SET company_id = 'R133_COMP_B' WHERE device_id = 'DEV_STAFF_A';
        RAISE EXCEPTION 'TEST H FAILED: Allowed company_id mutation';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Cannot change company_id or uid%' THEN RAISE EXCEPTION 'TEST H FAILED: Wrong err %', SQLERRM; END IF;
    END;

    -- TEST I: Staff uid mutation (Expect Exception)
    BEGIN
        UPDATE public.connected_devices SET uid = '11111111-1111-1111-1111-111111111111' WHERE device_id = 'DEV_STAFF_A';
        RAISE EXCEPTION 'TEST I FAILED: Allowed uid mutation';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Cannot change company_id or uid%' THEN RAISE EXCEPTION 'TEST I FAILED: Wrong err %', SQLERRM; END IF;
    END;

    -- TEST J: Company A Mother -> Company B Device (Expect RLS or trigger deny)
    PERFORM set_config('request.jwt.claims', '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
    UPDATE public.connected_devices SET status = 'Active' WHERE device_id = 'DEV_STAFF_B';
    SELECT status INTO rec FROM public.connected_devices WHERE device_id = 'DEV_STAFF_B';
    IF rec.status != 'Pending' THEN RAISE EXCEPTION 'TEST J FAILED: Allowed cross-tenant update'; END IF;

    -- Set DEV_STAFF_A to Rejected by Mother
    UPDATE public.connected_devices SET status = 'Rejected' WHERE device_id = 'DEV_STAFF_A';

    -- TEST K: Rejected -> Active by Staff (Expect Exception)
    PERFORM set_config('request.jwt.claims', '{"sub": "33333333-3333-3333-3333-333333333333", "role": "authenticated"}', true);
    BEGIN
        UPDATE public.connected_devices SET status = 'Active' WHERE device_id = 'DEV_STAFF_A';
        RAISE EXCEPTION 'TEST K FAILED: Allowed unauthorized update';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Unauthorized device authorization change%' THEN RAISE EXCEPTION 'TEST K FAILED: Wrong err %', SQLERRM; END IF;
    END;

    -- TEST L: Rejected -> Pending by Staff (Expect Exception)
    BEGIN
        UPDATE public.connected_devices SET status = 'Pending' WHERE device_id = 'DEV_STAFF_A';
        RAISE EXCEPTION 'TEST L FAILED: Allowed unauthorized update';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Unauthorized device authorization change%' THEN RAISE EXCEPTION 'TEST L FAILED: Wrong err %', SQLERRM; END IF;
    END;

    RAISE NOTICE 'ALL SECURITY MATRIX TESTS (A-L) PASSED PERFECTLY!';
END $$;

-- Cleanup
DELETE FROM public.connected_devices WHERE company_id IN ('R133_COMP_A', 'R133_COMP_B');
DELETE FROM public.workspaces WHERE company_id IN ('R133_COMP_A', 'R133_COMP_B');
