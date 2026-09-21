import os
import glob

def replace_in_file(filepath, replacements):
    with open(filepath, 'r') as f:
        content = f.read()
    orig = content
    for old, new in replacements:
        content = content.replace(old, new)
    if content != orig:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Patched {filepath}")

kt_files = glob.glob('app/src/**/*.kt', recursive=True)
for file in kt_files:
    if "WorkspaceManager.kt" in file: continue # already patched
    replace_in_file(file, [
        ("currentTenantId", "currentCompanyId"),
        ("tenantId: String", "companyId: String"),
        ("tenantId = ", "companyId = "),
        ("tenantId,", "companyId,"),
        ("tenantId:", "companyId:"),
        ("val tenantId", "val companyId"),
        ("updateTenantAndSyncCode", "updateCompanyAndSyncCode")
    ])
