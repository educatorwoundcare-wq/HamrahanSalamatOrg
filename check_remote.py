import subprocess
import json

def run_sql(sql):
    with open("temp_query.sql", "w") as f:
        f.write(sql)
    res = subprocess.run(["npx", "supabase", "db", "query", "-f", "temp_query.sql", "--linked", "--output-format", "json"], capture_output=True, text=True)
    try:
        return json.loads(res.stdout) if res.stdout.strip() else []
    except json.JSONDecodeError:
        print("JSON parse error:", res.stdout)
        return []

print("=== POLICIES ===")
policies = run_sql("""
SELECT tablename, policyname, cmd, permissive, roles, qual, with_check
FROM pg_policies
WHERE tablename IN ('workspaces', 'connected_devices');
""")
for p in policies:
    print(p)

print("\n=== FUNCTIONS ===")
functions = run_sql("""
SELECT proname, prosecdef, proconfig, pg_get_userbyid(proowner) as owner, pg_get_functiondef(oid) as def
FROM pg_proc
WHERE proname IN ('resolve_workspace_by_sync_code', 'is_device_member_of_workspace', 'set_workspace_creator', 'prevent_device_self_elevation');
""")
for f in functions:
    print(f['proname'], "secdef:", f['prosecdef'], "config:", f['proconfig'])
    print(f['def'])
    print("---")
