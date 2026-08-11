with open('app/src/main/java/com/example/data/Entities.kt', 'a') as f:
    f.write('\n\ndata class SearchResults(\n    val patients: List<Patient>,\n    val employees: List<Employee>,\n    val services: List<Service>,\n    val transactions: List<FinancialTransaction>\n)\n')
