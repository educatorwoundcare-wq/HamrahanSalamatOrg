
SELECT proname, prosecdef, proconfig, pg_get_userbyid(proowner) as owner, pg_get_functiondef(oid) as def
FROM pg_proc
WHERE proname IN ('is_device_member_of_workspace');
