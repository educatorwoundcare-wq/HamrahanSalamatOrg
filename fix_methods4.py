with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace("fun importTariffs(uri: android.net.Uri, context: android.content.Context) {}", "fun importTariffs(inputStream: java.io.InputStream) {}")

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)

# Also fix the ServiceScreen resetAllServicesToOfficialTariffs
with open('app/src/main/java/com/example/ui/ServiceScreen.kt', 'r') as f:
    content_s = f.read()

content_s = content_s.replace('viewModel.resetAllServicesToOfficialTariffs(context)', 'viewModel.resetAllServicesToOfficialTariffs()')

with open('app/src/main/java/com/example/ui/ServiceScreen.kt', 'w') as f:
    f.write(content_s)
