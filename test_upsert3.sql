BEGIN;
SET LOCAL "request.jwt.claim.sub" = 'test-uid-123';
SET LOCAL "request.jwt.claim.role" = 'authenticated';
SET LOCAL ROLE authenticated;

SAVEPOINT insert1;
INSERT INTO public.connected_devices (device_id, company_id, uid, status, role, requested_role)
VALUES ('DEV-TEST-3', 'COMP-HAMRAHAN0C7602', 'test-uid-123', 'Pending', 'Staff', 'Staff')
ON CONFLICT (device_id) DO UPDATE SET status = EXCLUDED.status;
ROLLBACK TO SAVEPOINT insert1;

ROLLBACK;
