with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    lines = f.readlines()

props = """
    val companyName = kotlinx.coroutines.flow.MutableStateFlow("")
"""

for i, line in enumerate(lines):
    if "class HamrahanViewModelFactory" in line:
        lines.insert(i - 1, props)
        break

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.writelines(lines)
