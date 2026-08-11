with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(
    r'fun resetAllServicesToOfficialTariffs\(\) \{\}',
    r'''fun resetAllServicesToOfficialTariffs(inputStream: java.io.InputStream? = null) {
        // Mock implementation to satisfy compiler
    }''',
    content
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
