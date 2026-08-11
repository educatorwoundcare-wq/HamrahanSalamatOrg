with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace('serviceNames = serviceNames,', 'serviceName = serviceNames,')
content = content.replace('selectedServices = selectedServices,', '')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
