SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
SELECT auth.uid()::text, (SELECT auth.uid()::text) as auth_uid_sub, is_device_member_of_workspace('TEST_WORKSPACE_A');
