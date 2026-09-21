import sys

with open('app/src/main/java/com/example/data/TenantInterceptor.kt', 'r') as f:
    content = f.read()

content = content.replace('tenantId', 'companyId')

with open('app/src/main/java/com/example/data/TenantInterceptor.kt', 'w') as f:
    f.write(content)
