with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '.fallbackToDestructiveMigrationOnDowngrade()',
    '.fallbackToDestructiveMigration()\n                    .fallbackToDestructiveMigrationOnDowngrade()'
)

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'w') as f:
    f.write(content)
