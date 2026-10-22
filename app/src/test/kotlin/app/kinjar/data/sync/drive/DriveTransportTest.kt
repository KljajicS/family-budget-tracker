package app.kinjar.data.sync.drive

import android.app.Activity
import com.google.android.gms.auth.api.identity.AuthorizationClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

class DriveTransportTest {
    private val server = MockWebServer().apply { start() }
    private val tokens = FakeTokens()
    private val drive = DriveTransport(tokens, DriveTransport.defaultClient(), server.url("/"))

    @After fun stop() = server.shutdown()

    @Test fun `metadata requests and decodes trash status`() = runTest {
        for (trashed in listOf(true, false)) {
            server.enqueue(json("""{"id":"f","name":"f","mimeType":"text/plain","trashed":$trashed}"""))
            assertEquals(trashed, drive.metadata("f").trashed)
            assertTrue(server.takeRequest().requestUrl!!.queryParameter("fields")!!.split(',').contains("trashed"))
        }
    }

    @Test fun `account identity uses exact grant without acquiring another account token`() = runTest {
        tokens.pending = true
        server.enqueue(json("""{"user":{"emailAddress":"selected@example.com"}}"""))
        assertEquals("selected@example.com", drive.authorizedAccountEmail("selected-token"))
        val request = server.takeRequest()
        assertEquals("Bearer selected-token", request.getHeader("Authorization"))
        assertEquals("/drive/v3/about", request.requestUrl!!.encodedPath)
        assertEquals("user(emailAddress)", request.requestUrl!!.queryParameter("fields"))
    }

    @Test fun `missing or invalid account identity fails closed`() = runTest {
        for (body in listOf("{}", """{"user":{}}""", """{"user":{"emailAddress":""}}""", """{"user":{"emailAddress":"invalid"}}""")) {
            server.enqueue(json(body))
            assertEquals("ACCOUNT_IDENTITY_MISSING", failure<DriveException.AuthRequired> {
                drive.authorizedAccountEmail("selected-token")
            }.reason)
        }
        server.enqueue(json("not json"))
        failure<DriveException.Malformed> { drive.authorizedAccountEmail("selected-token") }
    }

    @Test fun `rejected identity token is cleared without retrying another account`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        failure<DriveException.AuthRequired> { drive.authorizedAccountEmail("selected-token") }
        assertEquals(listOf("selected-token"), tokens.invalidated)
        assertEquals(1, server.requestCount)
    }

    @Test fun `identity lookup preserves safe http errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody(errorBody("accessNotConfigured")))
        val error = failure<DriveException.Http> { drive.authorizedAccountEmail("selected-token") }
        assertEquals(403, error.code)
        assertEquals("accessNotConfigured", error.reason)
    }

    @Test fun `create folder posts drive file metadata with bearer token`() = runTest {
        server.enqueue(json("""{"id":"folder_1","name":"Family budget","mimeType":"${DriveTransport.FOLDER_MIME}","version":"3"}"""))
        val folder = drive.createFolder("Family budget", parentId = "root_A")
        assertEquals("folder_1", folder.id)
        assertEquals(3L, folder.version)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/drive/v3/files", request.requestUrl!!.encodedPath)
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"mimeType\":\"${DriveTransport.FOLDER_MIME}\""))
        assertTrue(body.contains("\"parents\":[\"root_A\"]"))
    }

    @Test fun `listing follows every page and encodes the folder query`() = runTest {
        server.enqueue(json("""{"files":[${file("a")},${file("b")}],"nextPageToken":"page 2/="}"""))
        server.enqueue(json("""{"files":[${file("c")}]}"""))
        assertEquals(listOf("a", "b", "c"), drive.listChildren("folder_1").map { it.id })
        val first = server.takeRequest().requestUrl!!
        assertEquals("'folder_1' in parents and trashed = false", first.queryParameter("q"))
        assertNull(first.queryParameter("pageToken"))
        assertEquals("page 2/=", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
    }

    @Test fun `failure on a later page fails the whole listing`() = runTest {
        server.enqueue(json("""{"files":[${file("a")}],"nextPageToken":"p2"}"""))
        server.enqueue(MockResponse().setResponseCode(503))
        val error = failure<DriveException.Http> { drive.listChildren("folder_1") }
        assertEquals(503, error.code)
        assertTrue(error.retryable)
    }

    @Test fun `listing rejects too many files and repeated page tokens`() = runTest {
        server.enqueue(json("""{"files":[${file("a")},${file("b")}],"nextPageToken":"p2"}"""))
        failure<DriveException.TooLarge> { drive.listChildren("folder_1", maxFiles = 1) }
        server.enqueue(json("""{"files":[],"nextPageToken":"same"}"""))
        server.enqueue(json("""{"files":[],"nextPageToken":"same"}"""))
        failure<DriveException.Malformed> { drive.listChildren("folder_1") }
    }

    @Test fun `download is bounded by size`() = runTest {
        server.enqueue(MockResponse().setBody("12345"))
        assertArrayEquals("12345".toByteArray(), drive.download("file_1", maxBytes = 5))
        assertEquals("/drive/v3/files/file_1", server.takeRequest().requestUrl!!.encodedPath)
        server.enqueue(MockResponse().setBody("123456"))
        failure<DriveException.TooLarge> { drive.download("file_1", maxBytes = 5) }
    }

    @Test fun `truncated download surfaces as io failure`() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(64 * 1024)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        val error = failure<IOException> { drive.download("file_1", maxBytes = 1024 * 1024) }
        assertFalse(error is DriveException.TooLarge)
    }

    @Test fun `create uploads multipart and update keeps the file id`() = runTest {
        server.enqueue(json(file("pub_1")))
        assertEquals("pub_1", drive.createFile("publisher-A.bin", "folder_1", byteArrayOf(1, 2, 3)).id)
        val create = server.takeRequest()
        assertEquals("/upload/drive/v3/files", create.requestUrl!!.encodedPath)
        assertEquals("multipart", create.requestUrl!!.queryParameter("uploadType"))
        assertTrue(create.getHeader("Content-Type")!!.startsWith("multipart/related"))

        server.enqueue(json(file("pub_1")))
        assertEquals("pub_1", drive.updateFile("pub_1", byteArrayOf(4)).id)
        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/upload/drive/v3/files/pub_1", update.requestUrl!!.encodedPath)
        assertEquals("media", update.requestUrl!!.queryParameter("uploadType"))

        server.enqueue(json(file("other")))
        failure<DriveException.Malformed> { drive.updateFile("pub_1", byteArrayOf(4)) }
    }

    @Test fun `oversized upload is rejected before any request`() = runTest {
        failure<DriveException.TooLarge> { drive.updateFile("pub_1", ByteArray(DriveTransport.MAX_UPLOAD_BYTES.toInt() + 1)) }
        assertEquals(0, server.requestCount)
    }

    @Test fun `expired token is cleared and retried once`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json(file("f")))
        assertEquals("f", drive.metadata("f").id)
        assertEquals(listOf("token-1"), tokens.invalidated)
        server.takeRequest()
        assertEquals("Bearer token-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `repeated unauthorized and pending consent report auth required`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        failure<DriveException.AuthRequired> { drive.metadata("f") }
        assertEquals(listOf("token-1", "token-2"), tokens.invalidated)
        tokens.pending = true
        failure<DriveException.AuthRequired> { drive.metadata("f") }
        assertEquals(2, server.requestCount)
    }

    @Test fun `redirects are not followed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/elsewhere")))
        assertEquals(302, failure<DriveException.Http> { drive.metadata("f") }.code)
        assertEquals(1, server.requestCount)
    }

    @Test fun `unsafe ids are rejected before any request`() = runTest {
        for (id in listOf("../x", "a/b", "a?b", "", "a b", "%2e%2e")) {
            failure<IllegalArgumentException> { drive.download(id, 10) }
        }
        server.enqueue(json(file("../evil")))
        failure<DriveException.Malformed> { drive.metadata("f") }
        assertEquals(1, server.requestCount)
    }

    @Test fun `share posts a writer permission for the email`() = runTest {
        server.enqueue(json("""{"id":"perm_9"}"""))
        assertEquals("perm_9", drive.shareWithEmail("folder_1", "peer@example.com"))
        val request = server.takeRequest()
        assertEquals("/drive/v3/files/folder_1/permissions", request.requestUrl!!.encodedPath)
        assertEquals("true", request.requestUrl!!.queryParameter("sendNotificationEmail"))
        assertEquals("""{"type":"user","role":"writer","emailAddress":"peer@example.com"}""", request.body.readUtf8())
        failure<IllegalArgumentException> { drive.shareWithEmail("folder_1", "not an email") }
    }

    @Test fun `forbidden reasons distinguish rate limits from missing file access`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody(errorBody("userRateLimitExceeded")))
        assertTrue(failure<DriveException.Http> { drive.metadata("f") }.retryable)
        server.enqueue(MockResponse().setResponseCode(403).setBody(errorBody("appNotAuthorizedToFile")))
        val denied = failure<DriveException.Http> { drive.metadata("f") }
        assertEquals("appNotAuthorizedToFile", denied.reason)
        assertFalse(denied.retryable)
        server.enqueue(MockResponse().setResponseCode(404).setBody(errorBody("peer@example.com")))
        assertNull(failure<DriveException.Http> { drive.metadata("f") }.reason)
        server.enqueue(MockResponse().setResponseCode(500).setBody("x".repeat(200 * 1024)))
        assertEquals(500, failure<DriveException.Http> { drive.metadata("f") }.code)
    }

    @Test fun `malformed metadata is reported`() = runTest {
        server.enqueue(json("""{"name":"no id"}"""))
        failure<DriveException.Malformed> { drive.metadata("f") }
    }

    @Test fun `picked file ids are split and validated`() {
        assertEquals(listOf("a", "b_2"), DriveAuthorizer.parsePickedFileIds(" a, b_2 ,"))
        assertEquals(emptyList<String>(), DriveAuthorizer.parsePickedFileIds(null))
        assertTrue(runCatching { DriveAuthorizer.parsePickedFileIds("a,../b") }.exceptionOrNull() is DriveException.Malformed)
    }

    @Test fun `generated file id is requested for drive files and validated`() = runTest {
        server.enqueue(json("""{"kind":"drive#generatedIds","space":"drive","ids":["gen_1"]}"""))
        assertEquals("gen_1", drive.generateFileId())
        val url = server.takeRequest().requestUrl!!
        assertEquals("/drive/v3/files/generateIds", url.encodedPath)
        assertEquals(listOf("1", "drive", "files"), listOf("count", "space", "type").map { url.queryParameter(it) })
        for (body in listOf("""{"ids":[]}""", """{"ids":["a","b"]}""", """{"ids":["../x"]}""")) {
            server.enqueue(json(body))
            failure<DriveException.Malformed> { drive.generateFileId() }
        }
    }

    @Test fun `create with generated id sends id marker and mime without parents`() = runTest {
        server.enqueue(json(file("gen_1")))
        drive.createFile("probe.txt", null, "x".toByteArray(), "text/plain", "gen_1", mapOf("kjrProbe" to "v1"))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("""{"name":"probe.txt","mimeType":"text/plain","id":"gen_1","appProperties":{"kjrProbe":"v1"}}"""))
        assertTrue(body.contains("Content-Type: text/plain"))
        assertFalse(body.contains("parents"))
        failure<IllegalArgumentException> { drive.createFile("p", null, byteArrayOf(1), id = "../x") }
        assertEquals(1, server.requestCount)
    }

    @Test fun `app property patch is metadata only and sends just the changed keys`() = runTest {
        server.enqueue(json(file("f", """"appProperties":{"kjrProbe":"v1","kjrReq1.a":"v1.x"}""")))
        val updated = drive.updateAppProperties("f", mapOf("kjrReq1.a" to "v1.x"))
        assertEquals(mapOf("kjrProbe" to "v1", "kjrReq1.a" to "v1.x"), updated.appProperties)
        val write = server.takeRequest()
        assertEquals("PATCH", write.method)
        assertEquals("/drive/v3/files/f", write.requestUrl!!.encodedPath)
        assertNull(write.requestUrl!!.queryParameter("uploadType"))
        assertTrue(write.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("""{"appProperties":{"kjrReq1.a":"v1.x"}}""", write.body.readUtf8())

        server.enqueue(json(file("f")))
        drive.updateAppProperties("f", mapOf("kjrReq1.a" to null))
        assertEquals("""{"appProperties":{"kjrReq1.a":null}}""", server.takeRequest().body.readUtf8())

        server.enqueue(json(file("other")))
        failure<DriveException.Malformed> { drive.updateAppProperties("f", mapOf("k" to "v")) }
    }

    @Test fun `app property bounds are enforced before any request`() = runTest {
        val key = "k".repeat(60)
        server.enqueue(json(file("f")))
        drive.updateAppProperties("f", mapOf(key to "v".repeat(DriveTransport.MAX_PROPERTY_BYTES - 60)))
        val rejected = listOf(
            emptyMap<String, String>(),
            mapOf(key to "v".repeat(DriveTransport.MAX_PROPERTY_BYTES - 59)),
            mapOf(key to "š".repeat(33)),
            mapOf("a/b" to "v"),
            mapOf("" to "v"),
            mapOf("k" to "line\nbreak"),
            (0..DriveTransport.MAX_APP_PROPERTIES).associate { "k$it" to "v" },
        )
        for (changes in rejected) failure<IllegalArgumentException> { drive.updateAppProperties("f", changes) }
        assertEquals(1, server.requestCount)
    }

    @Test fun `metadata parses ownership and rejects oversized app properties`() = runTest {
        server.enqueue(json(file("f", """"ownedByMe":false,"appProperties":{"kjrProbe":"v1"}""")))
        val parsed = drive.metadata("f")
        assertEquals(false, parsed.ownedByMe)
        assertEquals(mapOf("kjrProbe" to "v1"), parsed.appProperties)
        val tooMany = (0..DriveTransport.MAX_APP_PROPERTIES).joinToString(",") { "\"k$it\":\"v\"" }
        server.enqueue(json(file("f", """"appProperties":{$tooMany}""")))
        failure<DriveException.Malformed> { drive.metadata("f") }
        server.enqueue(json(file("f", """"appProperties":{"k":"${"v".repeat(DriveTransport.MAX_PROPERTY_BYTES)}"}""")))
        failure<DriveException.Malformed> { drive.metadata("f") }
    }

    @Test fun `media update can keep a text mime type`() = runTest {
        server.enqueue(json(file("f")))
        drive.updateFile("f", "x".toByteArray(), "text/plain")
        assertTrue(server.takeRequest().getHeader("Content-Type")!!.startsWith("text/plain"))
    }

    @Test fun `exact pick accepts only the requested file`() {
        DriveAuthorizer.requireExactPick("peer_1", listOf("peer_1"))
        for (picked in listOf(emptyList<String>(), listOf("other"), listOf("peer_1", "other"))) {
            val error = runCatching { DriveAuthorizer.requireExactPick("peer_1", picked) }.exceptionOrNull()
            assertEquals("PICKED_FILE_MISMATCH", (error as DriveException.AuthRequired).reason)
        }
    }

    @Test fun `cancelled picker result is reported without reading the intent`() {
        val client = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(AuthorizationClient::class.java)) { _, _, _ ->
            error("client must not be called")
        } as AuthorizationClient
        val error = runCatching { DriveAuthorizer(client).resultFromActivity(Activity.RESULT_CANCELED, null) }.exceptionOrNull()
        assertTrue(error is DriveException.Cancelled)
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun file(id: String, extra: String? = null) =
        """{"id":"$id","name":"$id.bin","mimeType":"application/octet-stream","size":"3"${extra?.let { ",$it" }.orEmpty()}}"""
    private fun errorBody(reason: String) = """{"error":{"code":403,"errors":[{"reason":"$reason"}]}}"""

    private suspend inline fun <reified T : Throwable> failure(crossinline block: suspend () -> Unit): T {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue("expected ${T::class.simpleName}, got $error", error is T)
        return error as T
    }

    private class FakeTokens : DriveTokenSource {
        private var issued = 0
        var pending = false
        val invalidated = mutableListOf<String>()
        override suspend fun accessToken(): String {
            if (pending) throw DriveException.AuthRequired("consent pending")
            return "token-${++issued}"
        }
        override suspend fun invalidate(token: String) {
            invalidated += token
        }
    }
}
