with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    vm_content = f.read()

import re
replacement = """fun checkAndGenerateFixedExpensesForCurrentMonth() {
        viewModelScope.launch {
            val templates = repository.dao.getFixedExpenseTemplatesList()
            val now = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = now
            val currentMonth = calendar.get(java.util.Calendar.MONTH)
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            
            val allExpenses = repository.dao.getExpensesList()
            
            for (template in templates) {
                if (!template.isActive) continue
                
                // Check if expense exists for this template in current month
                val exists = allExpenses.any { exp -> 
                    val expCal = java.util.Calendar.getInstance()
                    expCal.timeInMillis = exp.paymentDate
                    expCal.get(java.util.Calendar.MONTH) == currentMonth && 
                    expCal.get(java.util.Calendar.YEAR) == currentYear &&
                    exp.title.contains(template.title)
                }
                
                if (!exists) {
                    val expCal = java.util.Calendar.getInstance()
                    expCal.set(java.util.Calendar.DAY_OF_MONTH, template.paymentDay)
                    val newExpense = com.example.data.Expense(
                        title = "هزینه ثابت: ${template.title}",
                        amount = template.monthlyAmount,
                        paymentDate = expCal.timeInMillis,
                        paymentMethod = "نقدی",
                        category = template.category,
                        description = "ایجاد خودکار هزینه ثابت"
                    )
                    repository.insertExpense(newExpense)
                }
            }
        }
    }"""
    
vm_content = re.sub(
    r'fun checkAndGenerateFixedExpensesForCurrentMonth\(\) \{\}',
    replacement,
    vm_content
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(vm_content)
