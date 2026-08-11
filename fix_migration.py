import re

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'r') as f:
    content = f.read()

migration_18_19 = """        private val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `DashboardCache` (`key` TEXT NOT NULL, `dataJson` TEXT NOT NULL, `updatedTimestamp` INTEGER NOT NULL, `uuid` TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }"""

content = content.replace(
    '        val ALL_MIGRATIONS: List<androidx.room.migration.Migration> = listOf(',
    migration_18_19 + '\n\n        val ALL_MIGRATIONS: List<androidx.room.migration.Migration> = listOf('
)

content = content.replace(
    'MIGRATION_14_15',
    'MIGRATION_14_15,\n            MIGRATION_18_19'
)

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'w') as f:
    f.write(content)
