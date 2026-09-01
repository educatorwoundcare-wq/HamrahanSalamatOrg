import re
with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'r') as f:
    text = f.read()

# Remove MIGRATION_22_23 from addMigrations
text = text.replace(
    '.addMigrations(MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)',
    '.addMigrations(MIGRATION_20_21, MIGRATION_21_22)'
)

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'w') as f:
    f.write(text)
