package app.kinjar.data.sync.crypto

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class MalformedBytes : Exception()

internal class ByteWriter {
    private val out = ByteArrayOutputStream()

    fun byte(value: Int) = apply { out.write(value) }
    fun int(value: Int) = apply { for (shift in 24 downTo 0 step 8) out.write(value ushr shift) }
    fun long(value: Long) = apply { for (shift in 56 downTo 0 step 8) out.write((value ushr shift).toInt()) }
    fun raw(bytes: ByteArray) = apply { out.write(bytes, 0, bytes.size) }
    fun short(bytes: ByteArray) = apply {
        require(bytes.size <= 255)
        byte(bytes.size)
        raw(bytes)
    }
    fun bytes(): ByteArray = out.toByteArray()
}

/** Every read is bounds checked; any overrun or leftover byte is [MalformedBytes]. */
internal class ByteReader(private val data: ByteArray) {
    var position = 0
        private set

    val remaining get() = data.size - position

    fun byte(): Int {
        if (remaining < 1) throw MalformedBytes()
        return data[position++].toInt() and 0xFF
    }

    fun int(): Int {
        var value = 0
        repeat(4) { value = (value shl 8) or byte() }
        return value
    }

    fun long(): Long {
        var value = 0L
        repeat(8) { value = (value shl 8) or byte().toLong() }
        return value
    }

    fun raw(count: Int): ByteArray {
        if (count < 0 || count > remaining) throw MalformedBytes()
        return data.copyOfRange(position, position + count).also { position += count }
    }

    fun short(): ByteArray = raw(byte())

    fun end() {
        if (remaining != 0) throw MalformedBytes()
    }
}

private val SYNC_ID = Regex("[A-Za-z0-9_-]{1,64}")

/** Family, device and member ids: ASCII only, so they are safe in file names and fixed-width headers. */
internal fun isSyncId(value: String) = SYNC_ID.matches(value)

internal fun String.ascii(): ByteArray = toByteArray(Charsets.US_ASCII)

internal fun ByteReader.syncId(): String = String(short(), Charsets.US_ASCII).takeIf(::isSyncId) ?: throw MalformedBytes()

internal fun syncDir(context: Context) = File(context.noBackupFilesDir, "sync")

/** Readers see the old file or the new one, never a torn write. */
internal fun writeAtomically(target: File, bytes: ByteArray) {
    val dir = target.absoluteFile.parentFile!!
    dir.mkdirs()
    val temp = File(dir, target.name + ".tmp")
    FileOutputStream(temp).use {
        it.write(bytes)
        it.fd.sync()
    }
    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
}
