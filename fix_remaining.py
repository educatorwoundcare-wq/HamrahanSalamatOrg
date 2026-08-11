import re

# Fix HamrahanViewModel.kt duplicate properties
with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    vm_content = f.read()

# I see conflicting companyJoinError, companyJoinSuccess
# The one I added earlier might be duplicating existing ones
vm_content = re.sub(r'    val companyJoinError = kotlinx\.coroutines\.flow\.MutableStateFlow<String\?>\(null\)\n    val companyJoinSuccess = kotlinx\.coroutines\.flow\.MutableStateFlow<String\?>\(null\)\n', '', vm_content)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(vm_content)
