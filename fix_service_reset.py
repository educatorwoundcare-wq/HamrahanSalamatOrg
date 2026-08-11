with open('app/src/main/java/com/example/ui/ServiceScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('viewModel.resetAllServicesToOfficialTariffs(context)', 'viewModel.resetAllServicesToOfficialTariffs()')

with open('app/src/main/java/com/example/ui/ServiceScreen.kt', 'w') as f:
    f.write(content)
