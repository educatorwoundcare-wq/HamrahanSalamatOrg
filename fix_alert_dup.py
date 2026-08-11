with open('app/src/main/java/com/example/data/Entities.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(
    r'val relatedScreen: String = "",\n    val isRead: Boolean = false,\n    val isDismissed: Boolean = false,\n    val relatedScreen: String\? = null,',
    r'val isRead: Boolean = false,\n    val isDismissed: Boolean = false,\n    val relatedScreen: String = "",',
    content
)

with open('app/src/main/java/com/example/data/Entities.kt', 'w') as f:
    f.write(content)
