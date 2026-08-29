with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

# Fix syntax error line 340
content = content.replace('}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "مرکز خدمات سلامت همکاران") }', '}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "مرکز خدمات سلامت همکاران")')

# Fix line 330 syntax error
content = content.replace('Log.d("PAIRING_RECEIVER", "PAIRING_RECEIVER_MASTER_CHECK isMaster=$isMaster role=$role status=$status companyId=$compId") }', 'Log.d("PAIRING_RECEIVER", "PAIRING_RECEIVER_MASTER_CHECK isMaster=$isMaster role=$role status=$status companyId=$compId")')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
