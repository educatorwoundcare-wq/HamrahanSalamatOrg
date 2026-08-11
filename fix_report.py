with open('app/src/main/java/com/example/ui/ReportScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('viewModel.exportDataToExcel(os, listOf("All"))', 'viewModel.exportDataToExcel(os)')

with open('app/src/main/java/com/example/ui/ReportScreen.kt', 'w') as f:
    f.write(content)
