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
DEV_B = "DEV_B"

sql = f"""
BEGIN;
SET LOCAL role = authenticated;
SELECT set_config('request.jwt.claims', format('{{"sub": "%s", "role": "authenticated"}}', '{UID_A}'::text), true);
UPDATE connected_devices SET status = 'Inactive' WHERE device_id = '{DEV_B}' RETURNING status;
COMMIT;
"""
print("Update output:", run_query(sql))

sql_verify = f"""
SELECT status FROM connected_devices WHERE device_id = '{DEV_B}';
"""
print("Verify output:", run_query(sql_verify))

