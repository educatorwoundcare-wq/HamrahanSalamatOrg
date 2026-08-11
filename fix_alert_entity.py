with open('app/src/main/java/com/example/data/Entities.kt', 'r') as f:
    content = f.read()

import re
if 'val relatedScreen: String = ""' not in content:
    content = re.sub(
        r'val timestamp: Long = System\.currentTimeMillis\(\),',
        r'val timestamp: Long = System.currentTimeMillis(),\n    val relatedScreen: String = "",',
        content
    )

with open('app/src/main/java/com/example/data/Entities.kt', 'w') as f:
    f.write(content)
