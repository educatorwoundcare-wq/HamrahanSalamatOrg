BEGIN;

-- 1. Create a dummy user
CREATE ROLE test_user NOLOGIN;
GRANT usage ON SCHEMA public TO test_user;
GRANT ALL ON public.workspaces TO test_user;
GRANT ALL ON public.connected_devices TO test_user;

-- 2. Switch to test user & set JWT claims correctly for auth.uid()
SET LOCAL ROLE test_user;
SET LOCAL "request.jwt.claim.sub" = 'test-uid-123';
SET LOCAL "request.jwt.claim.role" = 'authenticated';

-- 3. Check auth.uid()
SELECT auth.uid();

-- 4. Attempt simple INSERT (not upsert) to see if INSERT policy allows it
SAVEPOINT before_insert;
INSERT INTO public.connected_devices (device_id, company_id, uid, status, role, requested_role)
VALUES ('DEV-TEST', 'COMP-HAMRAHAN0C7602', 'test-uid-123', 'Pending', 'Staff', 'Staff');
-- check if error
ROLLBACK TO SAVEPOINT before_insert;

-- 5. Attempt UPSERT
SAVEPOINT before_upsert;
INSERT INTO public.connected_devices (device_id, company_id, uid, status, role, requested_role)
VALUES ('DEV-TEST-2', 'COMP-HAMRAHAN0C7602', 'test-uid-123', 'Pending', 'Staff', 'Staff')
ON CONFLICT (device_id) DO UPDATE SET status = EXCLUDED.status;
ROLLBACK TO SAVEPOINT before_upsert;

ROLLBACK;
