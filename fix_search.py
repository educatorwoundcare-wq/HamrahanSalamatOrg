with open('app/src/main/java/com/example/ui/SearchScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('val patients by patients.collectAsState()', 'val patients by viewModel.patients.collectAsState()')
content = content.replace('val employees by employees.collectAsState()', 'val employees by viewModel.employees.collectAsState()')
content = content.replace('val services by services.collectAsState()', 'val services by viewModel.services.collectAsState()')
content = content.replace('val transactions by transactions.collectAsState()', 'val transactions by viewModel.transactions.collectAsState()')

content = content.replace('patients.filter', 'patients.value.filter')
content = content.replace('employees.filter', 'employees.value.filter')
content = content.replace('services.filter', 'services.value.filter')
content = content.replace('transactions.filter', 'transactions.value.filter')

with open('app/src/main/java/com/example/ui/SearchScreen.kt', 'w') as f:
    f.write(content)
