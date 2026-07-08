package com.chatroom.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.chatroom.app.data.model.ApiAccount
import com.chatroom.app.data.model.Identity
import com.chatroom.app.data.model.Session
import com.chatroom.app.data.model.UserProfile
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.io.File

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val userProfiles: List<UserProfile> = emptyList(),
    val activeUserId: String? = null,
    val apiAccounts: List<ApiAccount> = emptyList(),
    val activeApiAccountId: String? = null,
    val identities: List<Identity> = emptyList(),
    val activeIdentityId: String? = null,
    val sessions: List<Session> = emptyList(),
    val activeSessionId: String? = null
)

class BackupManager(private val context: Context) {

    private val gson = Gson()
    private val userProfileRepo = UserProfileRepository(context)
    private val apiAccountRepo = ApiAccountRepository(context)
    private val identityRepo = IdentityRepository(context)
    private val sessionRepo = SessionRepository(context)

    companion object {
        private const val BACKUP_FILENAME = "ChatRoom_backup.json"
    }

    /** Collect all data from all repositories and return the backup data object. */
    suspend fun collectBackupData(): BackupData {
        return BackupData(
            userProfiles = userProfileRepo.profiles.first(),
            activeUserId = userProfileRepo.activeProfile.first()?.id,
            apiAccounts = apiAccountRepo.accounts.first(),
            activeApiAccountId = apiAccountRepo.activeAccount.first()?.id,
            identities = identityRepo.identities.first(),
            activeIdentityId = identityRepo.activeIdentity.first()?.id,
            sessions = sessionRepo.sessions.first(),
            activeSessionId = sessionRepo.activeSession.first()?.id
        )
    }

    /** Export backup data to the Downloads folder. Returns success or error message. */
    suspend fun exportBackup(): Result<String> = runCatching {
        val data = collectBackupData()
        val json = gson.toJson(data)
        val bytes = json.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore on Android 10+
            writeViaMediaStore(bytes)
        } else {
            // Direct file write on older Android
            writeDirectFile(bytes)
        }

        "已导出到 Download/$BACKUP_FILENAME"
    }

    /** Import backup data from the Downloads folder. Returns success or error message. */
    suspend fun importBackup(): Result<String> = runCatching {
        val json = readBackupFile() ?: throw java.io.FileNotFoundException(
            "备份文件不存在: Download/$BACKUP_FILENAME\n尝试手动选择文件恢复"
        )
        restoreFromJson(json)
    }

    /** Import backup data from a user-picked URI (SAF file picker). */
    suspend fun importBackupFromUri(uri: Uri): Result<String> = runCatching {
        val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes().toString(Charsets.UTF_8)
        } ?: throw java.io.FileNotFoundException("无法读取选择的文件")
        restoreFromJson(json)
    }

    /** Check if a backup file exists in the Downloads folder. */
    suspend fun hasBackupFile(): Boolean {
        return try {
            findBackupFileUri() != null || getDownloadFile().exists()
        } catch (e: Exception) {
            false
        }
    }

    /** Try to restore from backup if no data exists. Returns true if restored. */
    suspend fun autoRestoreIfNeeded(): Boolean {
        val hasProfiles = userProfileRepo.profiles.first().isNotEmpty()
        val hasAccounts = apiAccountRepo.accounts.first().isNotEmpty()
        if (hasProfiles || hasAccounts) return false // Data already exists, no need to restore

        if (!hasBackupFile()) return false

        val result = importBackup()
        return result.isSuccess
    }

    private suspend fun restoreFromJson(json: String): String {
        val data: BackupData = gson.fromJson(json, BackupData::class.java)
        userProfileRepo.replaceAll(data.userProfiles, data.activeUserId)
        apiAccountRepo.replaceAll(data.apiAccounts, data.activeApiAccountId)
        identityRepo.replaceAll(data.identities, data.activeIdentityId)
        sessionRepo.replaceAll(data.sessions, data.activeSessionId)
        return "已从备份恢复 ${data.userProfiles.size} 个用户资料, " +
                "${data.apiAccounts.size} 个API密钥, " +
                "${data.identities.size} 个身份, " +
                "${data.sessions.size} 个会话"
    }

    // ─── API 29+ MediaStore ───────────────────────────────────────────

    private fun writeViaMediaStore(bytes: ByteArray) {
        // Remove existing backup file first to avoid duplicates
        deleteExistingMediaStoreFile()

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILENAME)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.IS_PENDING, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw java.io.IOException("无法创建备份文件")

        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun deleteExistingMediaStoreFile() {
        try {
            val existingUri = queryMediaStoreUri()
            if (existingUri != null) {
                context.contentResolver.delete(existingUri, null, null)
            }
        } catch (_: Exception) { }
    }

    private fun queryMediaStoreUri(): Uri? {
        val collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(BACKUP_FILENAME)

        context.contentResolver.query(collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return Uri.withAppendedPath(collectionUri, id.toString())
            }
        }
        return null
    }

    // ─── API < 29 direct file ──────────────────────────────────────────

    private fun getDownloadFile(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, BACKUP_FILENAME)
    }

    private fun writeDirectFile(bytes: ByteArray) {
        getDownloadFile().writeBytes(bytes)
    }

    private fun readBackupFile(): String? {
        // Try MediaStore first (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = queryMediaStoreUri()
            if (uri != null) {
                return context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().toString(Charsets.UTF_8)
                }
            }
        }

        // Fallback: direct file path (works after reinstall on most devices)
        return try {
            val file = getDownloadFile()
            if (file.exists()) file.readBytes().toString(Charsets.UTF_8) else null
        } catch (_: Exception) {
            null
        }
    }
}
