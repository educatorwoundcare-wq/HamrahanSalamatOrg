import subprocess
import json

def run_query(sql):
    with open("temp.sql", "w") as f:
        f.write(sql)
    cmd = ["npx", "supabase", "db", "query", "-f", "temp.sql", "--linked", "--output-format", "json"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    try:
        return json.loads(res.stdout) if res.stdout.strip() else []
    except:
        return res.stdout

print(run_query("SELECT status, uid, device_id FROM connected_devices WHERE device_id = 'DEV_E';"))
