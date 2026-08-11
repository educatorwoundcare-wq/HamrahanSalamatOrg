import re

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'CREATE TABLE IF NOT EXISTS `DashboardCache`',
    'CREATE TABLE IF NOT EXISTS `dashboard_caches`'
)

with open('app/src/main/java/com/example/data/HamrahanDatabase.kt', 'w') as f:
    f.write(content)
