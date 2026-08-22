with open('app/src/main/java/com/example/data/CloudClient.kt', 'r') as f:
    content = f.read()

# Find the end of the good syncPull
good_end = content.find('        } catch (e: Exception) {\n            return@withContext Result.failure(e)\n        }\n    }')
if good_end != -1:
    good_end += len('        } catch (e: Exception) {\n            return@withContext Result.failure(e)\n        }\n    }')
    content = content[:good_end] + "\n}\n"

with open('app/src/main/java/com/example/data/CloudClient.kt', 'w') as f:
    f.write(content)
