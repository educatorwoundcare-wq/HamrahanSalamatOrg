import re

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'r') as f:
    content = f.read()

content = content.replace('version = 19,', 'version = 20,')

migrations_20 = """        private val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `dashboard_caches` (`key` TEXT NOT NULL, `dataJson` TEXT NOT NULL, `updatedTimestamp` INTEGER NOT NULL, `uuid` TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }
        private val MIGRATION_18_20 = object : androidx.room.migration.Migration(18, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `dashboard_caches` (`key` TEXT NOT NULL, `dataJson` TEXT NOT NULL, `updatedTimestamp` INTEGER NOT NULL, `uuid` TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }"""

content = content.replace(
    '        private val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {\n            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {\n                db.execSQL("CREATE TABLE IF NOT EXISTS `dashboard_caches` (`key` TEXT NOT NULL, `dataJson` TEXT NOT NULL, `updatedTimestamp` INTEGER NOT NULL, `uuid` TEXT NOT NULL, PRIMARY KEY(`key`))")\n            }\n        }',
    migrations_20
)

content = content.replace(
    'MIGRATION_14_15,\n            MIGRATION_18_19',
    'MIGRATION_14_15,\n            MIGRATION_18_20,\n            MIGRATION_19_20'
)

content = content.replace(
    '.fallbackToDestructiveMigration(dropAllTables = true)',
    '.fallbackToDestructiveMigration()'
)

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'w') as f:
    f.write(content)
