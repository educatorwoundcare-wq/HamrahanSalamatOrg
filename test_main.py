import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()
if "AlertDialog" in content:
    print("Crash handler is injected.")
