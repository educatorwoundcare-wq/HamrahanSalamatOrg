with open('app/src/main/java/com/example/ui/ServiceScreen.kt', 'r') as f:
    content = f.read()

# Fix ServiceScreen importTariffs usage and resetAllServicesToOfficialTariffs usage
content = content.replace('viewModel.importTariffs(inputStream, context)', 'viewModel.importTariffs(uri, context)')

# Oh wait, ServiceScreen.kt has:
# Argument type mismatch: actual type is 'InputStream', but 'Uri' was expected.
# file:///app/applet/app/src/main/java/com/example/ui/ServiceScreen.kt:61:45
# We can change ViewModel to accept InputStream instead of Uri. Let's do that!

with open('app/src/main/java/com/example/ui/ServiceScreen.kt', 'w') as f:
    f.write(content)
