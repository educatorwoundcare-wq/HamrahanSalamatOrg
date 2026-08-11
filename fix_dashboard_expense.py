import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val todayExpense = activeExpenses.sumOf { it.amount } + transactions.filter { it.type == "هزینه" && (it.referenceId == null || it.referenceId == 0) }.sumOf { it.amount }',
    'val todayExpense = activeExpenses.sumOf { it.amount } + activeRegs.sumOf { it.employeeCommission } + transactions.filter { it.type == "هزینه" && (it.referenceId == null || it.referenceId == 0) }.sumOf { it.amount }'
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
