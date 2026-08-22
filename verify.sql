-- Check Functions
SELECT proname, prosecdef, pg_get_userbyid(proowner) as owner 
FROM pg_proc 
WHERE proname IN ('resolve_workspace_by_sync_code', 'is_device_member_of_workspace', 'set_workspace_creator', 'prevent_device_self_elevation');

-- Check Triggers
SELECT tgname, relname as table_name
FROM pg_trigger t
JOIN pg_class c ON t.tgrelid = c.oid
WHERE tgname IN ('set_workspace_creator_trigger', 'prevent_device_self_elevation_trigger');

-- Check Constraints
SELECT conname, contype, relname 
FROM pg_constraint c
JOIN pg_class t ON c.conrelid = t.oid
WHERE conname IN ('workspaces_sync_code_key', 'connected_devices_company_device_key');

-- Check RLS
SELECT relname, relrowsecurity 
FROM pg_class 
WHERE relname IN ('workspaces', 'connected_devices');

-- Check Policies
SELECT tablename, policyname 
FROM pg_policies 
WHERE tablename IN ('workspaces', 'connected_devices');

-- Check Function Privileges (resolve_workspace_by_sync_code)
SELECT grantee, privilege_type
FROM information_schema.routine_privileges
WHERE routine_name = 'resolve_workspace_by_sync_code' AND privilege_type = 'EXECUTE';
