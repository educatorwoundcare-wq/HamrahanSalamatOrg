SELECT
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd,
    qual,
    with_check
FROM pg_policies
WHERE schemaname = 'public'
AND tablename = 'connected_devices'
ORDER BY policyname;

SELECT
    relrowsecurity,
    relforcerowsecurity
FROM pg_class
WHERE oid = 'public.connected_devices'::regclass;

SELECT
    auth.uid() AS current_uid,
    auth.role() AS current_role;
