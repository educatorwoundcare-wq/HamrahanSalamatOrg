import re

with open("app/src/main/java/com/example/data/DeviceIdentityProvider.kt", "r") as f:
    content = f.read()

content = content.replace("import com.example.data.local.SystemSetting\n", "")
content = content.replace("import com.example.data.local.HamrahanDao\n", "")

with open("app/src/main/java/com/example/data/DeviceIdentityProvider.kt", "w") as f:
    f.write(content)
print("Removed bad imports!")
