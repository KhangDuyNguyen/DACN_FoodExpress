package vn.hutech.foodexpress.driver.data.session

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverSessionStore @Inject constructor(@ApplicationContext context: Context) {
    // App-private storage, excluded from Android backup/device transfer. No password is stored.
    private val tokenFile = AtomicFile(File(context.noBackupFilesDir, "driver_session"))

    fun readToken(): String? = try {
        // openRead also restores AtomicFile's backup after an interrupted write on older Android.
        tokenFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            .takeIf { it.isNotBlank() }
    } catch (error: FileNotFoundException) {
        if (tokenFile.baseFile.exists() || File(tokenFile.baseFile.path + ".bak").exists()) throw error
        null
    }

    fun saveToken(token: String) {
        val stream = tokenFile.startWrite()
        try {
            stream.write(token.toByteArray(Charsets.UTF_8))
            tokenFile.finishWrite(stream)
        } catch (error: Exception) {
            tokenFile.failWrite(stream)
            throw error
        }
    }

    fun clear() {
        tokenFile.delete()
        check(!tokenFile.baseFile.exists()) { "Could not clear driver session" }
    }
}
