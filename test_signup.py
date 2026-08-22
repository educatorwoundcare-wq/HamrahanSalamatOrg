import urllib.request
import urllib.error
import json

URL = "https://qfbjkdhhgeomrbamkpnn.supabase.co"
ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFmYmprZGhoZ2VvbXJiYW1rcG5uIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMDUzOTEsImV4cCI6MjEwMTY4MTM5MX0.TBU2hyj3jBM7wvl2cK6MhAtjv1J5fiIcN-uKTBjBSAk"

def request(method, path, data=None):
    headers = {"apikey": ANON_KEY}
    if data:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(f"{URL}{path}", headers=headers, data=data, method=method)
    try:
        with urllib.request.urlopen(req) as res:
            return res.getcode(), json.loads(res.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode('utf-8'))

code, res = request("POST", "/auth/v1/signup", b'{"email": "test1@example.com", "password": "password123"}')
print(code, res)
