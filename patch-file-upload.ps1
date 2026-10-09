# patch-file-upload.ps1
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = "C:\Users\ADMIN\Desktop\PathfinderHub\app\src\main\java\com\pathfinder\hub"
$backupDir = "C:\Users\ADMIN\Desktop\PathfinderHub\_backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"

Write-Host "Backup dir: $backupDir" -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

$filesToBackup = @(
    "$root\data\repository\LevelRepository.kt",
    "$root\data\repository\HonorRepository.kt",
    "$root\data\local\dao\HonorDao.kt",
    "$root\ui\screens\levels\LevelDetailViewModel.kt",
    "$root\ui\navigation\PathfinderNavHost.kt"
)
foreach ($f in $filesToBackup) {
    if (Test-Path $f) {
        $rel = $f.Substring($root.Length + 1).Replace("\", "_")
        Copy-Item $f "$backupDir\$rel"
    }
}

# ============================================================
# 1. Создать FileUploadResultHandler.kt
# ============================================================
$handlerPath = "$root\domain\service\FileUploadResultHandler.kt"
New-Item -ItemType Directory -Force -Path (Split-Path $handlerPath) | Out-Null

$handlerLines = @(
    'package com.pathfinder.hub.domain.service'
    ''
    'import android.util.Log'
    'import com.pathfinder.hub.data.local.SessionManager'
    'import com.pathfinder.hub.data.repository.HonorRepository'
    'import com.pathfinder.hub.data.repository.LevelRepository'
    'import com.pathfinder.hub.ui.storage.FileUploadContext'
    'import com.pathfinder.hub.ui.storage.FileUploadResult'
    'import javax.inject.Inject'
    'import javax.inject.Singleton'
    ''
    '@Singleton'
    'class FileUploadResultHandler @Inject constructor('
    '    private val levelRepository: LevelRepository,'
    '    private val honorRepository: HonorRepository,'
    '    private val sessionManager: SessionManager'
    ') {'
    ''
    '    suspend fun handle(result: FileUploadResult): Boolean {'
    '        val userId = sessionManager.getUserId()'
    '        if (userId.isNullOrBlank()) {'
    '            Log.w(TAG, "no userId")'
    '            return false'
    '        }'
    ''
    '        return try {'
    '            when (result.context) {'
    '                FileUploadContext.LEVEL_REQUIREMENT -> {'
    '                    levelRepository.attachReportToRequirement('
    '                        userId = userId,'
    '                        requirementId = result.contextId,'
    '                        fileUrl = result.fileUrl,'
    '                        fileName = result.fileName'
    '                    )'
    '                    true'
    '                }'
    '                FileUploadContext.HONOR_REQUIREMENT -> {'
    '                    honorRepository.attachReportToRequirement('
    '                        userId = userId,'
    '                        requirementId = result.contextId,'
    '                        fileUrl = result.fileUrl,'
    '                        fileName = result.fileName'
    '                    )'
    '                    true'
    '                }'
    '                FileUploadContext.CHAT -> {'
    '                    Log.d(TAG, "CHAT handled in ClubChatScreen")'
    '                    false'
    '                }'
    '                FileUploadContext.TASK,'
    '                FileUploadContext.AVATAR,'
    '                FileUploadContext.EVENT,'
    '                FileUploadContext.NEWSPAPER,'
    '                FileUploadContext.GENERIC -> {'
    '                    Log.d(TAG, "TODO: " + result.context + " - " + result.fileUrl)'
    '                    false'
    '                }'
    '            }'
    '        } catch (e: Exception) {'
    '            Log.e(TAG, "handle failed for " + result.context, e)'
    '            false'
    '        }'
    '    }'
    ''
    '    companion object {'
    '        private const val TAG = "FileUploadResultHandler"'
    '    }'
    '}'
)
Set-Content -Path $handlerPath -Value $handlerLines -Encoding UTF8
Write-Host "OK: FileUploadResultHandler.kt" -ForegroundColor Green

# ============================================================
# 2. LevelRepository.kt - add attachReportToRequirement
# ============================================================
$levelRepoPath = "$root\data\repository\LevelRepository.kt"
$c = Get-Content $levelRepoPath -Raw

if ($c -notmatch 'attachReportToRequirement') {
    if ($c -notmatch 'import java.util.Date') {
        $c = $c -replace 'import kotlinx.coroutines.flow.Flow', "import kotlinx.coroutines.flow.Flow`r`nimport java.util.Date"
    }

    $anchor = 'suspend fun upsertProgress(p: LevelProgressEntity) {' + "`r`n" +
              '        dao.upsertProgress(p)' + "`r`n" +
              '        syncQueueManager.enqueue(' + "`r`n" +
              '            entityType = "level_progress",' + "`r`n" +
              '            entityId = "${p.userId}_${p.requirementId}",' + "`r`n" +
              '            operation = "UPDATE",' + "`r`n" +
              '            payload = p' + "`r`n" +
              '        )' + "`r`n" +
              '    }'

    $insert = "`r`n`r`n" +
        '    suspend fun attachReportToRequirement(' + "`r`n" +
        '        userId: String,' + "`r`n" +
        '        requirementId: String,' + "`r`n" +
        '        fileUrl: String,' + "`r`n" +
        '        fileName: String' + "`r`n" +
        '    ) {' + "`r`n" +
        '        val existing = dao.getProgressForRequirement(userId, requirementId)' + "`r`n" +
        '        val levelId = existing?.levelId ?: ""' + "`r`n" +
        '' + "`r`n" +
        '        val updated = LevelProgressEntity(' + "`r`n" +
        '            userId = userId,' + "`r`n" +
        '            levelId = levelId,' + "`r`n" +
        '            requirementId = requirementId,' + "`r`n" +
        '            status = "submitted",' + "`r`n" +
        '            submittedAt = Date(),' + "`r`n" +
        '            approvedBy = existing?.approvedBy,' + "`r`n" +
        '            approvedAt = existing?.approvedAt,' + "`r`n" +
        '            comment = "File: $fileName ($fileUrl)",' + "`r`n" +
        '            pendingSync = true' + "`r`n" +
        '        )' + "`r`n" +
        '' + "`r`n" +
        '        upsertProgress(updated)' + "`r`n" +
        '    }'

    if ($c.Contains($anchor)) {
        $c = $c.Replace($anchor, $anchor + $insert)
        Set-Content -Path $levelRepoPath -Value $c -Encoding UTF8
        Write-Host "OK: LevelRepository.kt" -ForegroundColor Green
    } else {
        Write-Host "WARN: anchor not found in LevelRepository.kt" -ForegroundColor Yellow
    }
} else {
    Write-Host "SKIP: LevelRepository.kt already has method" -ForegroundColor Gray
}

# ============================================================
# 3. HonorRepository.kt
# ============================================================
$honorRepoPath = "$root\data\repository\HonorRepository.kt"
$c = Get-Content $honorRepoPath -Raw

if ($c -notmatch 'attachReportToRequirement') {
    if ($c -notmatch 'import java.util.Date') {
        $c = $c -replace 'import kotlinx.coroutines.flow.Flow', "import kotlinx.coroutines.flow.Flow`r`nimport java.util.Date"
    }

    $anchor = 'suspend fun upsertProgress(p: HonorProgressEntity) {' + "`r`n" +
              '        dao.upsertProgress(p)' + "`r`n" +
              '        syncQueueManager.enqueue(' + "`r`n" +
              '            entityType = "honor_progress",' + "`r`n" +
              '            entityId = "${p.userId}_${p.requirementId}",' + "`r`n" +
              '            operation = "UPDATE",' + "`r`n" +
              '            payload = p' + "`r`n" +
              '        )' + "`r`n" +
              '    }'

    $insert = "`r`n`r`n" +
        '    suspend fun attachReportToRequirement(' + "`r`n" +
        '        userId: String,' + "`r`n" +
        '        requirementId: String,' + "`r`n" +
        '        fileUrl: String,' + "`r`n" +
        '        fileName: String' + "`r`n" +
        '    ) {' + "`r`n" +
        '        val existing = dao.getProgressForRequirement(userId, requirementId)' + "`r`n" +
        '        val honorId = existing?.honorId ?: ""' + "`r`n" +
        '' + "`r`n" +
        '        upsertProgress(' + "`r`n" +
        '            HonorProgressEntity(' + "`r`n" +
        '                userId = userId,' + "`r`n" +
        '                honorId = honorId,' + "`r`n" +
        '                requirementId = requirementId,' + "`r`n" +
        '                status = "submitted",' + "`r`n" +
        '                submittedAt = Date(),' + "`r`n" +
        '                approvedBy = existing?.approvedBy,' + "`r`n" +
        '                approvedAt = existing?.approvedAt,' + "`r`n" +
        '                mediaUrls = (existing?.mediaUrls ?: emptyList()) + fileUrl,' + "`r`n" +
        '                comment = "File: $fileName",' + "`r`n" +
        '                pendingSync = true' + "`r`n" +
        '            )' + "`r`n" +
        '        )' + "`r`n" +
        '    }'

    if ($c.Contains($anchor)) {
        $c = $c.Replace($anchor, $anchor + $insert)
        Set-Content -Path $honorRepoPath -Value $c -Encoding UTF8
        Write-Host "OK: HonorRepository.kt" -ForegroundColor Green
    } else {
        Write-Host "WARN: anchor not found in HonorRepository.kt" -ForegroundColor Yellow
    }
} else {
    Write-Host "SKIP: HonorRepository.kt already has method" -ForegroundColor Gray
}

# ============================================================
# 4. HonorDao.kt
# ============================================================
$honorDaoPath = "$root\data\local\dao\HonorDao.kt"
$c = Get-Content $honorDaoPath -Raw

if ($c -notmatch 'getProgressForRequirement') {
    $anchor = '    @Query("SELECT * FROM honor_progress WHERE userId = :userId AND honorId = :honorId")' + "`r`n" +
              '    suspend fun getProgressSnapshot(userId: String, honorId: String): List<HonorProgressEntity>'

    $insert = "`r`n`r`n" +
        '    @Query("SELECT * FROM honor_progress WHERE userId = :userId AND requirementId = :reqId LIMIT 1")' + "`r`n" +
        '    suspend fun getProgressForRequirement(userId: String, reqId: String): HonorProgressEntity?'

    if ($c.Contains($anchor)) {
        $c = $c.Replace($anchor, $anchor + $insert)
        Set-Content -Path $honorDaoPath -Value $c -Encoding UTF8
        Write-Host "OK: HonorDao.kt" -ForegroundColor Green
    } else {
        Write-Host "WARN: anchor not found in HonorDao.kt" -ForegroundColor Yellow
    }
} else {
    Write-Host "SKIP: HonorDao.kt already has method" -ForegroundColor Gray
}

# ============================================================
# 5. LevelDetailViewModel.kt
# ============================================================
$vmPath = "$root\ui\screens\levels\LevelDetailViewModel.kt"
$c = Get-Content $vmPath -Raw

if ($c -notmatch 'attachUploadedFile') {
    if ($c -notmatch 'import com.pathfinder.hub.ui.storage.FileUploadResult') {
        $c = $c -replace 'import com.pathfinder.hub.data.repository.LevelRepository', "import com.pathfinder.hub.data.repository.LevelRepository`r`nimport com.pathfinder.hub.ui.storage.FileUploadResult"
    }

    $anchor = '    fun onRequirementAction(requirementId: String, type: String) {' + "`r`n" +
              '        Log.d(TAG, "onRequirementAction: requirementId=$requirementId, type=$type")' + "`r`n" +
              '    }'

    $insert = "`r`n`r`n" +
        '    fun attachUploadedFile(result: FileUploadResult) {' + "`r`n" +
        '        val userId = sessionManager.getUserId() ?: return' + "`r`n" +
        '        viewModelScope.launch {' + "`r`n" +
        '            try {' + "`r`n" +
        '                levelRepository.attachReportToRequirement(' + "`r`n" +
        '                    userId = userId,' + "`r`n" +
        '                    requirementId = result.contextId,' + "`r`n" +
        '                    fileUrl = result.fileUrl,' + "`r`n" +
        '                    fileName = result.fileName' + "`r`n" +
        '                )' + "`r`n" +
        '                Log.d(TAG, "File attached: " + result.contextId)' + "`r`n" +
        '            } catch (e: Exception) {' + "`r`n" +
        '                Log.e(TAG, "attachUploadedFile failed", e)' + "`r`n" +
        '                _state.update { it.copy(errorMessage = e.message ?: "attach failed") }' + "`r`n" +
        '            }' + "`r`n" +
        '        }' + "`r`n" +
        '    }'

    if ($c.Contains($anchor)) {
        $c = $c.Replace($anchor, $anchor + $insert)
        Set-Content -Path $vmPath -Value $c -Encoding UTF8
        Write-Host "OK: LevelDetailViewModel.kt" -ForegroundColor Green
    } else {
        Write-Host "WARN: anchor not found in LevelDetailViewModel.kt" -ForegroundColor Yellow
    }
} else {
    Write-Host "SKIP: LevelDetailViewModel.kt already has method" -ForegroundColor Gray
}

# ============================================================
# 6. PathfinderNavHost.kt - two blocks
# ============================================================
$navPath = "$root\ui\navigation\PathfinderNavHost.kt"
$c = Get-Content $navPath -Raw

# LEVEL_DETAIL
if ($c -notmatch 'savedStateHandle = backStackEntry\.savedStateHandle') {
    $oldLevel = '                onUploadReport = { lvlId, requirementId ->' + "`r`n" +
                '                    navController.navigate(' + "`r`n" +
                '                        Routes.fileUpload(' + "`r`n" +
                '                            contextType = "LEVEL_REQUIREMENT",' + "`r`n" +
                '                            contextId = requirementId' + "`r`n" +
                '                        )' + "`r`n" +
                '                    )' + "`r`n" +
                '                }' + "`r`n" +
                '            )'

    $newLevel = '                onUploadReport = { lvlId, requirementId ->' + "`r`n" +
                '                    navController.navigate(' + "`r`n" +
                '                        Routes.fileUpload(' + "`r`n" +
                '                            contextType = "LEVEL_REQUIREMENT",' + "`r`n" +
                '                            contextId = requirementId' + "`r`n" +
                '                        )' + "`r`n" +
                '                    )' + "`r`n" +
                '                },' + "`r`n" +
                '                savedStateHandle = backStackEntry.savedStateHandle' + "`r`n" +
                '            )'

    if ($c.Contains($oldLevel)) {
        $c = $c.Replace($oldLevel, $newLevel)
        Write-Host "OK: LEVEL_DETAIL patched" -ForegroundColor Green
    } else {
        Write-Host "WARN: LEVEL_DETAIL anchor not found" -ForegroundColor Yellow
    }
} else {
    Write-Host "SKIP: LEVEL_DETAIL already patched" -ForegroundColor Gray
}

# HONOR_REQUIREMENT_DETAIL
if ($c -notmatch 'navBackStackEntry = backStackEntry') {
    $oldHonor = '                onOpenReport = {' + "`r`n" +
                '                    // \u2705 \u041f\u0435\u0440\u0435\u0434\u0430\u0451\u043c \u0440\u0435\u0430\u043b\u044c\u043d\u044b\u0439 requirementId' + "`r`n" +
                '                    navController.navigate(' + "`r`n" +
                '                        Routes.fileUpload(' + "`r`n" +
                '                            contextType = "HONOR_REQUIREMENT",' + "`r`n" +
                '                            contextId = requirementId' + "`r`n" +
                '                        )' + "`r`n" +
                '                    )' + "`r`n" +
                '                }' + "`r`n" +
                '            )'

    # fallback: anchor without comment
    $oldHonorSimple = '                onOpenReport = {' + "`r`n" +
                '                    navController.navigate(' + "`r`n" +
                '                        Routes.fileUpload(' + "`r`n" +
                '                            contextType = "HONOR_REQUIREMENT",' + "`r`n" +
                '                            contextId = requirementId' + "`r`n" +
                '                        )' + "`r`n" +
                '                    )' + "`r`n" +
                '                }' + "`r`n" +
                '            )'

    $newHonor = '                onOpenReport = {' + "`r`n" +
                '                    navController.navigate(' + "`r`n" +
                '                        Routes.fileUpload(' + "`r`n" +
                '                            contextType = "HONOR_REQUIREMENT",' + "`r`n" +
                '                            contextId = requirementId' + "`r`n" +
                '                        )' + "`r`n" +
                '                    )' + "`r`n" +
                '                },' + "`r`n" +
                '                navBackStackEntry = backStackEntry' + "`r`n" +
                '            )'

    $patched = $false
    if ($c.Contains($oldHonor)) {
        $c = $c.Replace($oldHonor, $newHonor)
        $patched = $true
    } elseif ($c.Contains($oldHonorSimple)) {
        $c = $c.Replace($oldHonorSimple, $newHonor)
        $patched = $true
    }

    if ($patched) {
        Write-Host "OK: HONOR_REQUIREMENT_DETAIL patched" -ForegroundColor Green
    } else {
        Write-Host "WARN: HONOR_REQUIREMENT_DETAIL anchor not found" -ForegroundColor Yellow
    }
} else {
    Write-Host "SKIP: HONOR_REQUIREMENT_DETAIL already patched" -ForegroundColor Gray
}

Set-Content -Path $navPath -Value $c -Encoding UTF8

Write-Host ""
Write-Host "DONE. Next steps:" -ForegroundColor Cyan
Write-Host "  1. LevelDetailScreen.kt - replace manually (see chat)" -ForegroundColor Cyan
Write-Host "  2. HonorRequirementDetailScreen.kt - replace manually (see chat)" -ForegroundColor Cyan
Write-Host "  3. .\gradlew clean assembleDebug" -ForegroundColor Cyan