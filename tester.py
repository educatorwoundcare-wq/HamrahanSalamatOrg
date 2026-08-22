import subprocess
import json
import uuid

def run_sql(sql):
    try:
        # Save to temp file
        with open("temp.sql", "w") as f:
            f.write(sql)
        res = subprocess.run(["npx", "supabase", "db", "query", "-f", "temp.sql", "--linked", "--output-format", "json"], capture_output=True, text=True)
        if res.returncode != 0:
            return {"error": res.stderr}
        if not res.stdout.strip():
            return []
        try:
            return json.loads(res.stdout)
        except json.JSONDecodeError:
            return res.stdout
    except Exception as e:
        return {"error": str(e)}

# Setup
setup_sql = """
CREATE TABLE IF NOT EXISTS test_results (
    test_id text,
    status text,
    details text
);
TRUNCATE test_results;
"""
run_sql(setup_sql)

print("Setup completed.")
