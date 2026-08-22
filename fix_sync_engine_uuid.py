import re

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'r') as f:
    content = f.read()

content = re.sub(r'    private suspend fun getUuidForLocalId.*?\n    \}', '', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'w') as f:
    f.write(content)
