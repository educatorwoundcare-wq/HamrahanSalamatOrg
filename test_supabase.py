import urllib.request
import json

URL = "https://qfbjkdhhgeomrbamkpnn.supabase.co"
ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFmYmprZGhoZ2VvbXJiYW1rcG5uIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMDUzOTEsImV4cCI6MjEwMTY4MTM5MX0.TBU2hyj3jBM7wvl2cK6MhAtjv1J5fiIcN-uKTBjBSAk"

def test_rest(path):
    req = urllib.request.Request(f"{URL}/rest/v1/{path}", headers={"apikey": ANON_KEY, "Authorization": f"Bearer {ANON_KEY}"})
    try:
        with urllib.request.urlopen(req) as response:
            return response.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        return f"Error: {e.code} - {e.read().decode('utf-8')}"

print("Workspaces:", test_rest("workspaces?select=*"))
print("Devices:", test_rest("connected_devices?select=*"))
