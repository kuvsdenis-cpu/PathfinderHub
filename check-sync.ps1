$PackageName = "com.pathfinder.hub"

Write-Host "=== 1. adb ==="
Get-Command adb -ErrorAction SilentlyContinue | Format-List

Write-Host "=== 2. Devices ==="
adb devices

Write-Host "=== 3. App process ==="
adb shell pidof $PackageName

Write-Host "=== 4. Sync queue ==="
adb shell "run-as $PackageName sqlite3 databases/pathfinder_db 'SELECT id, entityType, operation, status, retryCount FROM sync_queue_items;'"

Write-Host "=== 5. WorkManager jobs ==="
adb shell dumpsys jobscheduler | Select-String $PackageName

Write-Host "=== 6. SyncWorker logs ==="
adb logcat -d -t 300 | Select-String "SyncWorker|SyncQueue|FirestoreSyncService"

Write-Host "=== Done ==="