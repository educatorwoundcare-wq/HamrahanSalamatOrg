with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace('repository.deleteExpense(expense, reason = "حذف دستی هزینه", comment = "")', 'repository.deleteExpense(expense)')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
