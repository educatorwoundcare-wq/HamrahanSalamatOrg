import re

with open('app/src/main/java/com/example/HamrahanApplication.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '''    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }''',
    '''    override fun onCreate() {
        super.onCreate()
        instance = this
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        container = AppContainer(this)
    }'''
)

with open('app/src/main/java/com/example/HamrahanApplication.kt', 'w') as f:
    f.write(content)
