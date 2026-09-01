import re

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'r') as f:
    text = f.read()

migration_23 = """
        val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Empty migration, assuming no schema changes, or handled elsewhere.
            }
        }
"""

if "MIGRATION_22_23" not in text:
    text = text.replace(
        'val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {',
        migration_23 + '\n        val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {'
    )

    text = text.replace(
        '.addMigrations(MIGRATION_20_21, MIGRATION_21_22)',
        '.addMigrations(MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)\n                    .fallbackToDestructiveMigration()'
    )

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'w') as f:
    f.write(text)
