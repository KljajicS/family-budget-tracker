package app.kinjar.data.sync.identity

import android.content.Context
import app.kinjar.data.sync.crypto.ascii
import app.kinjar.data.sync.crypto.isSyncId
import app.kinjar.data.sync.crypto.syncDir
import app.kinjar.data.sync.crypto.writeAtomically
import java.io.File
import java.io.IOException

/** Which family member uses this phone. Local to the install: never synced, backed up or tied to the install id. */
class SelectedMemberStore(dir: File) {
    private val file = File(dir, "selected_member")

    @Synchronized
    fun get(): String? = try {
        file.takeIf { it.exists() && it.length() <= 64 }?.readText(Charsets.US_ASCII)?.takeIf(::isSyncId)
    } catch (_: IOException) {
        null
    }

    @Synchronized
    fun set(memberId: String) {
        require(isSyncId(memberId)) { "Invalid member id" }
        writeAtomically(file, memberId.ascii())
    }

    @Synchronized
    fun clear() {
        if (file.exists() && !file.delete()) throw IOException("Could not clear the selected member")
    }

    companion object {
        fun forApp(context: Context) = SelectedMemberStore(syncDir(context))
    }
}
