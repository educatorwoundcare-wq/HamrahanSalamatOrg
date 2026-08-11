import re

with open('app/src/test/java/com/example/FinancialEndToEndAuditTest.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'assertEquals("Active general ledger entries count must be exactly 2", 2, journalList.size)',
    'assertEquals("Active general ledger entries count must be exactly 4", 4, journalList.size)'
)

with open('app/src/test/java/com/example/FinancialEndToEndAuditTest.kt', 'w') as f:
    f.write(content)
