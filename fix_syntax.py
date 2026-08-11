import re

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '''private val MIGRATION_14_15,
            MIGRATION_18_19 = object : androidx.room.migration.Migration(14, 15) {''',
    '''private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {'''
)

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'w') as f:
    f.write(content)
