import re

with open('app/src/main/java/com/example/data/HamrahanRepository.kt', 'r') as f:
    content = f.read()

replacement = """    suspend fun deleteFinancialReportById(id: Int) {
        dao.runInTransaction {
            val report = dao.getFinancialReportsList().find { it.id == id }
            if (report != null) {
                dao.deleteFinancialReportById(id)
                registerLocalChange("FinancialReport", report.uuid, "DELETE")
            }
        }
    }"""

content = re.sub(r'    suspend fun deleteFinancialReportById\(id: Int\) \{.*?\}', replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/HamrahanRepository.kt', 'w') as f:
    f.write(content)
