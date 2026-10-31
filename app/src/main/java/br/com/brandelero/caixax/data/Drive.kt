package br.com.brandelero.caixax.data

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.brandelero.caixax.App
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Backup do arquivo do banco na pasta oculta do app no Google Drive (appDataFolder). */
object Drive {
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    private const val KEEP = 5
    private val SQLITE_HEADER = "SQLite format 3".toByteArray()

    val authRequest: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata")))
        .build()

    /** Token sem interface; null quando o usuário ainda precisa autorizar. */
    suspend fun silentToken(ctx: Context): String? {
        val r = Identity.getAuthorizationClient(ctx).authorize(authRequest).await()
        return if (r.hasResolution()) null else r.accessToken
    }

    fun backupIds(token: String): List<String> {
        val q = URLEncoder.encode("name='${CaixaDb.NAME}'", "UTF-8")
        val json = JSONObject(httpGet("$API/files?spaces=appDataFolder&q=$q&orderBy=modifiedTime%20desc&fields=files(id)", token))
        val files = json.getJSONArray("files")
        return (0 until files.length()).map { files.getJSONObject(it).getString("id") }
    }

    suspend fun upload(app: App, token: String) = withContext(Dispatchers.IO) {
        // Junta o WAL ao arquivo principal para o backup ficar completo
        app.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use {
            check(it.moveToFirst() && it.getInt(0) == 0) { "Banco ocupado, tento de novo depois" }
        }
        val bytes = app.getDatabasePath(CaixaDb.NAME).readBytes()
        val old = backupIds(token)
        val boundary = "caixax-" + UUID.randomUUID()
        val body = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
            "{\"name\":\"${CaixaDb.NAME}\",\"parents\":[\"appDataFolder\"]}\r\n" +
            "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray() +
            bytes + "\r\n--$boundary--\r\n".toByteArray()
        request("$UPLOAD/files?uploadType=multipart", "POST", token, "multipart/related; boundary=$boundary", body)
        // Mantém as KEEP cópias mais recentes; só apaga depois que a nova subiu
        old.drop(KEEP - 1).forEach { request("$API/files/$it", "DELETE", token) }
    }

    /** Baixa o backup mais recente para um arquivo temporário; null se não houver backup. */
    suspend fun download(ctx: Context, token: String): File? = withContext(Dispatchers.IO) {
        val id = backupIds(token).firstOrNull() ?: return@withContext null
        val tmp = File(ctx.cacheDir, "restore.db")
        val c = URL("$API/files/$id?alt=media").openConnection() as HttpURLConnection
        try {
            c.setRequestProperty("Authorization", "Bearer $token")
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            c.disconnect()
        }
        val header = ByteArray(SQLITE_HEADER.size).also { b -> tmp.inputStream().use { it.read(b) } }
        check(header.contentEquals(SQLITE_HEADER)) { "Arquivo de backup inválido" }
        // Arquivo truncado passaria no cabeçalho; confere a integridade antes de substituir o banco
        SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") { "Backup corrompido" } }
        }
        tmp
    }

    /** Substitui o banco pelo arquivo restaurado e reinicia o app. */
    fun replaceDatabaseAndRestart(app: App, file: File) {
        app.db.close()
        val path = app.getDatabasePath(CaixaDb.NAME)
        path.copyTo(File(path.path + ".bak"), overwrite = true) // cópia do banco atual, caso algo dê errado
        file.copyTo(path, overwrite = true)
        File(path.path + "-wal").delete()
        File(path.path + "-shm").delete()
        file.delete()
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        Runtime.getRuntime().exit(0)
    }

    private fun request(url: String, method: String, token: String, type: String? = null, body: ByteArray? = null) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.requestMethod = method
            c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        } finally {
            c.disconnect()
        }
    }

    fun schedule(ctx: Context) {
        val work = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("backup", ExistingPeriodicWorkPolicy.KEEP, work)
    }

    fun unschedule(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork("backup")
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        return try {
            val token = Drive.silentToken(app) ?: return Result.failure()
            Drive.upload(app, token)
            app.prefs.lastBackup = System.currentTimeMillis()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
