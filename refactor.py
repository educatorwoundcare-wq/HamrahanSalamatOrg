import re

with open('app/src/main/java/com/example/data/HamrahanRepository.kt', 'r') as f:
    content = f.read()

# Replace the signature of registerLocalChange to accept operationType
content = content.replace(
    'fun registerLocalChange(entityType: String, entityId: String, isDeleted: Boolean = false) {',
    'fun registerLocalChange(entityType: String, entityId: String, operationType: String = "UPDATE") {'
)
content = content.replace(
    'isDeleted: Boolean = false',
    'operationType: String = "UPDATE"'
)

# We will just rewrite registerLocalChange implementation entirely.
# Let's write it in a multi_edit_file call instead of Python for the implementation.
