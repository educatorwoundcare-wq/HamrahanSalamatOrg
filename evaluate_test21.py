import subprocess
import json

def run_query(sql, as_json=True):
    with open("temp.sql", "w") as f:
        f.write(sql)
    cmd = ["npx", "supabase", "db", "query", "-f", "temp.sql", "--linked", "--output-format", "json"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        return res.stderr
    try:
        return json.loads(res.stdout) if res.stdout.strip() else []
    except:
        return res.stdout

UID_A = "11111111-1111-1111-1111-111111111111"
DEV_A = "DEV_A"

sql = f"""
BEGIN;
SET LOCAL role = authenticated;
SELECT set_config('request.jwt.claims', format('{{"sub": "%s", "role": "authenticated"}}', '{UID_A}'::text), true);
SELECT count(*) as c FROM connected_devices;
COMMIT;
"""
print("Select output:", run_query(sql))

