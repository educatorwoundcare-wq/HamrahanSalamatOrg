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

print("\n=== FUNCTIONS ===")
functions = run_sql("""
SELECT proname, prosecdef, proconfig, pg_get_userbyid(proowner) as owner, pg_get_functiondef(oid) as def
FROM pg_proc
WHERE proname IN ('is_device_member_of_workspace');
""")
for f in functions:
    print(f['proname'], "secdef:", f['prosecdef'], "config:", f['proconfig'])
    print(f['def'])
    print("---")
