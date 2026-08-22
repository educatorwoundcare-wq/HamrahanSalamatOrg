import subprocess, json

def run_query(sql):
    with open("temp.sql", "w") as f:
        f.write(sql)
    cmd = ["npx", "supabase", "db", "query", "-f", "temp.sql", "--linked", "--output-format", "json"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print("Error:", res.stderr)
        return None
    try:
        return json.loads(res.stdout) if res.stdout.strip() else []
    except Exception as e:
        print("Parse error:", e, res.stdout)
        return res.stdout

sql = """
SELECT pg_get_functiondef(oid) 
FROM pg_proc 
WHERE proname = 'prevent_device_self_elevation';
"""
print(run_query(sql))
