with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

import re
# Remove the first definition of DashboardMetrics
content = re.sub(r'data class DashboardMetrics\([\s\S]*?mostRequestedService: String = "-"\n\)', '', content, count=1)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
