with open('app/src/main/java/com/example/ui/DashboardScreen.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(
    r'selectedServices = null,',
    r'selectedServices = emptyList(),',
    content
)

with open('app/src/main/java/com/example/ui/DashboardScreen.kt', 'w') as f:
    f.write(content)
