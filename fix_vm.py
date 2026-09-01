import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    text = f.read()

text = text.replace('workspaceManager.clearWorkspaceTenantOnly()', 'com.example.data.WorkspaceManager.getInstance(repository.context).clearWorkspaceTenantOnly()')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(text)
