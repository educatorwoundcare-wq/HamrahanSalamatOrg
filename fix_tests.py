import os
import re

def fix_test(path):
    with open(path, 'r') as f:
        c = f.read()
    
    # HamrahanViewModel constructor in tests
    # Add nulls or mocks for the 3 missing use cases + supabase auth
    c = re.sub(r'(HamrahanViewModel\(\s*.*?repository,\s*.*?accountEngine)(\s*\))', r'\1, null, null, null\2', c, flags=re.DOTALL)
    
    # SyncEngine constructor
    c = re.sub(r'SyncEngine\(\s*context\s*,\s*testDao\s*\)', r'SyncEngine(testDao, CloudClient(testDao, context), context)', c)
    c = re.sub(r'syncEngine\.shutdown\(\)', r'// syncEngine.shutdown()', c)
    
    with open(path, 'w') as f:
        f.write(c)

for file in os.listdir('app/src/test/java/com/example'):
    if file.endswith('.kt'):
        fix_test(os.path.join('app/src/test/java/com/example', file))

