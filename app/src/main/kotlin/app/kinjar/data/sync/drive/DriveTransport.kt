package app.kinjar.data.sync.drive

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface DriveTokenSource {
    /** Returns a current drive.file access token or throws [DriveException.AuthRequired]; never prompts. */
    suspend fun accessToken(): String
    suspend fun invalidate(token: String)
}

sealed class DriveException(message: String) : IOException(message) {
    class AuthRequired(message: String) : DriveException(message)
    class Cancelled : DriveException("Drive authorization cancelled")
    class Http(val code: Int, val reason: String?) : DriveException("Drive request failed: HTTP $code${reason?.let { " $it" } ?: ""}") {
        val retryable: Boolean get() = code == 429 || code >= 500 ||
            (code == 403 && reason in setOf("rateLimitExceeded", "userRateLimitExceeded"))
    }
    class TooLarge(message: String) : DriveException(message)
    class Malformed(message: String) : DriveException(message)
}

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val modifiedTime: String?,
    val md5Checksum: String?,
    val version: Long?,
)

class DriveTransport(
    private val tokens: DriveTokenSource,
    private val client: OkHttpClient = defaultClient(),
    private val apiBase: HttpUrl = "https://www.googleapis.com/".toHttpUrl(),
) {
    suspend fun createFolder(name: String, parentId: String? = null): DriveFile {
        val metadata = Metadata(checkName(name), FOLDER_MIME, parentId?.let { listOf(checkId(it)) })
        val request = Request.Builder().url(files().addFields().build())
            .post(json.encodeToString(Metadata.serializer(), metadata).toRequestBody(JSON))
        return parseFile(send(request, MAX_JSON_BYTES))
    }

    suspend fun metadata(fileId: String): DriveFile =
        parseFile(send(Request.Builder().url(files(checkId(fileId)).addFields().build()).get(), MAX_JSON_BYTES))

    /** Lists every non-trashed child visible to this grant; any failed page fails the whole listing. */
    suspend fun listChildren(folderId: String, maxFiles: Int = 1000): List<DriveFile> {
        val query = "'${checkId(folderId)}' in parents and trashed = false"
        val result = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = files().addQueryParameter("q", query)
                .addQueryParameter("pageSize", PAGE_SIZE.toString())
                .addQueryParameter("fields", "nextPageToken,files($FILE_FIELDS)")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = decode(Page.serializer(), send(Request.Builder().url(url).get(), MAX_JSON_BYTES), "file listing")
            page.files.mapTo(result) { it.toFile() }
            if (result.size > maxFiles) throw DriveException.TooLarge("Folder has more than $maxFiles files")
            if (page.nextPageToken != null && page.nextPageToken == pageToken) throw DriveException.Malformed("Repeated page token")
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return result
    }

    suspend fun download(fileId: String, maxBytes: Long): ByteArray =
        send(Request.Builder().url(files(checkId(fileId)).addQueryParameter("alt", "media").build()).get(), maxBytes)

    suspend fun createFile(name: String, parentId: String, content: ByteArray): DriveFile {
        checkUploadSize(content)
        val metadata = Metadata(checkName(name), OCTET_STREAM.toString(), listOf(checkId(parentId)))
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(json.encodeToString(Metadata.serializer(), metadata).toRequestBody(JSON))
            .addPart(content.toRequestBody(OCTET_STREAM))
            .build()
        val url = uploads().addQueryParameter("uploadType", "multipart").addFields().build()
        return parseFile(send(Request.Builder().url(url).post(body), MAX_JSON_BYTES))
    }

    /** Replaces content in place so the file keeps the ID peers already know. */
    suspend fun updateFile(fileId: String, content: ByteArray): DriveFile {
        checkUploadSize(content)
        val url = uploads(checkId(fileId)).addQueryParameter("uploadType", "media").addFields().build()
        val file = parseFile(send(Request.Builder().url(url).patch(content.toRequestBody(OCTET_STREAM)), MAX_JSON_BYTES))
        if (file.id != fileId) throw DriveException.Malformed("Drive returned a different file")
        return file
    }

    /** Returns the permission ID. */
    suspend fun shareWithEmail(fileId: String, email: String, notify: Boolean = true): String {
        require(EMAIL.matches(email)) { "Invalid email address" }
        val url = files(checkId(fileId)).addPathSegment("permissions")
            .addQueryParameter("sendNotificationEmail", notify.toString())
            .addQueryParameter("fields", "id")
            .build()
        val body = json.encodeToString(Permission.serializer(), Permission("user", "writer", email)).toRequestBody(JSON)
        val reply = decode(IdOnly.serializer(), send(Request.Builder().url(url).post(body), MAX_JSON_BYTES), "permission response")
        return reply.id.takeIf { ID.matches(it) } ?: throw DriveException.Malformed("Invalid permission ID")
    }

    private suspend fun send(builder: Request.Builder, maxBytes: Long): ByteArray {
        repeat(2) {
            val token = tokens.accessToken()
            val reply = execute(builder.header("Authorization", "Bearer $token").build(), maxBytes)
            when {
                reply.code == 401 -> tokens.invalidate(token)
                reply.code in 200..299 -> return reply.body
                else -> throw DriveException.Http(reply.code, errorReason(reply.body))
            }
        }
        throw DriveException.AuthRequired("Drive rejected a fresh access token")
    }

    private suspend fun execute(request: Request, maxBytes: Long): Reply = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Drive request failed"))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use { read(it, maxBytes) } }
                    .recoverCatching { if (it is DriveException) throw it else throw IOException("Drive response interrupted") }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    private fun read(response: Response, maxBytes: Long): Reply {
        val body = response.body ?: return Reply(response.code, ByteArray(0))
        val source = body.source()
        if (!response.isSuccessful) {
            return Reply(response.code, if (source.request(MAX_ERROR_BYTES + 1)) ByteArray(0) else source.readByteArray())
        }
        if (body.contentLength() > maxBytes || source.request(maxBytes + 1)) throw DriveException.TooLarge("Drive response too large")
        return Reply(response.code, source.readByteArray())
    }

    private fun files(id: String? = null) = apiBase.newBuilder().addPathSegments("drive/v3/files").apply { id?.let(::addPathSegment) }
    private fun uploads(id: String? = null) = apiBase.newBuilder().addPathSegments("upload/drive/v3/files").apply { id?.let(::addPathSegment) }
    private fun HttpUrl.Builder.addFields() = addQueryParameter("fields", FILE_FIELDS)

    private fun parseFile(bytes: ByteArray): DriveFile = decode(WireFile.serializer(), bytes, "file metadata").toFile()

    private fun <T> decode(serializer: KSerializer<T>, bytes: ByteArray, what: String): T =
        runCatching { json.decodeFromString(serializer, bytes.decodeToString()) }
            .getOrElse { throw DriveException.Malformed("Invalid $what") }

    private class Reply(val code: Int, val body: ByteArray)

    @Serializable private data class Metadata(val name: String, val mimeType: String, val parents: List<String>? = null)
    @Serializable private data class Permission(val type: String, val role: String, val emailAddress: String)
    @Serializable private data class IdOnly(val id: String)
    @Serializable private data class Page(val files: List<WireFile> = emptyList(), val nextPageToken: String? = null)

    // Drive encodes int64 fields such as size and version as JSON strings.
    @Serializable private data class WireFile(
        val id: String,
        val name: String,
        val mimeType: String,
        val size: String? = null,
        val modifiedTime: String? = null,
        val md5Checksum: String? = null,
        val version: String? = null,
    ) {
        fun toFile(): DriveFile {
            if (!ID.matches(id)) throw DriveException.Malformed("Invalid file ID")
            return DriveFile(id, name, mimeType, size?.toLongOrNull(), modifiedTime, md5Checksum, version?.toLongOrNull())
        }
    }

    companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val MAX_UPLOAD_BYTES = 5L * 1024 * 1024
        private const val MAX_JSON_BYTES = 1L * 1024 * 1024
        private const val MAX_ERROR_BYTES = 64L * 1024
        private const val PAGE_SIZE = 100
        private const val FILE_FIELDS = "id,name,mimeType,size,modifiedTime,md5Checksum,version"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private val ID = Regex("[A-Za-z0-9_-]{1,256}")
        private val EMAIL = Regex("[^\\s@]{1,64}@[^\\s@]{1,190}\\.[^\\s@]{1,63}")
        private val REASON = Regex("[A-Za-z_]{1,64}")
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()

        internal fun checkId(id: String): String = id.also { require(ID.matches(it)) { "Invalid Drive ID" } }

        private fun checkName(name: String): String = name.also {
            require(it.isNotBlank() && it.length <= 255 && it.none(Char::isISOControl)) { "Invalid Drive file name" }
        }

        // ponytail: multipart and media uploads are capped at 5 MiB by Drive; switch to resumable uploads if history files outgrow it.
        private fun checkUploadSize(content: ByteArray) {
            if (content.size > MAX_UPLOAD_BYTES) throw DriveException.TooLarge("Upload exceeds $MAX_UPLOAD_BYTES bytes")
        }

        private fun errorReason(body: ByteArray): String? = runCatching {
            val error = json.parseToJsonElement(body.decodeToString()).jsonObject["error"]!!.jsonObject
            error["errors"]?.jsonArray?.firstOrNull()?.jsonObject?.get("reason")?.jsonPrimitive?.content
                ?: error["status"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { REASON.matches(it) }
    }
}
