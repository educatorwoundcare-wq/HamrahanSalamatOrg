import re

with open('app/src/main/java/com/example/data/HamrahanRepository.kt', 'r') as f:
    content = f.read()

content = content.replace('registerLocalChange("AuditLog", id.toString())', 'registerLocalChange("AuditLog", log.uuid)')
content = content.replace('registerLocalChange("FinancialEditHistory", id.toString())', 'registerLocalChange("FinancialEditHistory", history.uuid)')
content = content.replace('registerLocalChange("FinancialEditHistory", historyId.toString())', 'registerLocalChange("FinancialEditHistory", editHistory.uuid)')

content = content.replace('registerLocalChange("JournalEntry", id.toString())', 'registerLocalChange("JournalEntry", entry.uuid)')
content = content.replace('registerLocalChange("JournalEntry", newEntryId.toString())', 'registerLocalChange("JournalEntry", adjEntry.uuid)')
content = content.replace('val id = dao.insertJournalEntry(rev)\n                registerLocalChange("JournalEntry", entry.uuid)', 'val id = dao.insertJournalEntry(rev)\n                registerLocalChange("JournalEntry", rev.uuid)')

content = content.replace('registerLocalChange("FinancialReport", if (report.id != 0) report.id.toString() else id.toString())', 'registerLocalChange("FinancialReport", report.uuid)')
content = content.replace('registerLocalChange("FinancialReport", report.id.toString(), "DELETE")', 'registerLocalChange("FinancialReport", report.uuid, "DELETE")')

with open('app/src/main/java/com/example/data/HamrahanRepository.kt', 'w') as f:
    f.write(content)

