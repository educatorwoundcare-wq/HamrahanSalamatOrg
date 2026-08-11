with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    vm_content = f.read()

import re

# Fix saveExpense
vm_content = re.sub(
    r'fun saveExpense\(expense: com\.example\.data\.Expense, param1: String = "", param2: String = ""\) \{\}',
    r'''fun saveExpense(expense: com.example.data.Expense, param1: String = "", param2: String = "") {
        viewModelScope.launch {
            if (expense.id == 0) {
                repository.insertExpense(expense)
            } else {
                repository.updateExpense(expense, reason = param1, comment = param2)
            }
        }
    }''',
    vm_content
)

# Fix deleteExpense
vm_content = re.sub(
    r'fun deleteExpense\(expense: com\.example\.data\.Expense\) \{\}',
    r'''fun deleteExpense(expense: com.example.data.Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense, reason = "حذف دستی هزینه", comment = "")
        }
    }''',
    vm_content
)

# Fix saveFixedExpenseTemplate
vm_content = re.sub(
    r'fun saveFixedExpenseTemplate\(template: com\.example\.data\.FixedExpenseTemplate\) \{\}',
    r'''fun saveFixedExpenseTemplate(template: com.example.data.FixedExpenseTemplate) {
        viewModelScope.launch {
            if (template.id == 0) {
                repository.insertFixedExpenseTemplate(template)
            } else {
                repository.updateFixedExpenseTemplate(template)
            }
        }
    }''',
    vm_content
)

# Fix deleteFixedExpenseTemplate
vm_content = re.sub(
    r'fun deleteFixedExpenseTemplate\(template: com\.example\.data\.FixedExpenseTemplate\) \{\}',
    r'''fun deleteFixedExpenseTemplate(template: com.example.data.FixedExpenseTemplate) {
        viewModelScope.launch {
            repository.deleteFixedExpenseTemplate(template)
        }
    }''',
    vm_content
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(vm_content)
