with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(
    r'fun exportDataToExcel\(outputStream: java\.io\.OutputStream\): Boolean \{ return true \}',
    r'fun exportDataToExcel(outputStream: java.io.OutputStream, sheets: List<String> = emptyList()): Boolean { return true }',
    content
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
