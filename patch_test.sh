sed -i 's/val applyRemoteRow = .*//g' app/src/test/java/com/example/CanonicalUuidHardeningTest.kt
sed -i 's/applyRemoteRow.isAccessible = true//g' app/src/test/java/com/example/CanonicalUuidHardeningTest.kt
sed -i 's/applyRemoteRow.invoke(engine, row)/engine.applyRemoteRow(row)/g' app/src/test/java/com/example/CanonicalUuidHardeningTest.kt
sed -i 's/val retryMethod = .*//g' app/src/test/java/com/example/CanonicalUuidHardeningTest.kt
sed -i 's/retryMethod.isAccessible = true//g' app/src/test/java/com/example/CanonicalUuidHardeningTest.kt
sed -i 's/retryMethod.invoke(engine)/engine.retryPendingDependencies()/g' app/src/test/java/com/example/CanonicalUuidHardeningTest.kt
sed -i 's/val upsertLocally = .*//g' app/src/test/java/com/example/CanonicalUuidHardeningTest.kt
sed -i 's/upsertLocally.isAccessible = true//g' app/src/test/java/com/example/CanonicalUuidHardeningTest.kt
sed -i 's/upsertLocally.invoke(engine, "Patient", uuid2, pt2Incoming)/engine.upsertLocally("Patient", uuid2, pt2Incoming)/g' app/src/test/java/com/example/CanonicalUuidHardeningTest.kt
