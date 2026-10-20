package app.kinjar.data.receipt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import app.kinjar.domain.Receipt
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface ReceiptLookup {
    suspend fun fetch(raw: String): Receipt
}

class FiscalReceiptClient(private val client: OkHttpClient = defaultClient()) : ReceiptLookup {
    override suspend fun fetch(raw: String): Receipt = withContext(Dispatchers.IO) {
        val url = FiscalUrl.parse(raw)
        val header = FiscalReceiptParser.header(read(Request.Builder().url(url).build()))
        val body = FormBody.Builder().add("invoiceNumber", header.invoiceNumber).add("token", header.token).build()
        val details = read(Request.Builder().url("https://suf.purs.gov.rs/specifications").post(body).build())
        FiscalReceiptParser.receipt(url.toString(), header, details)
    }

    internal suspend fun read(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Receipt lookup failed"))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("Receipt lookup failed")
                        val body = it.body ?: throw IOException("Empty receipt")
                        val source = body.source()
                        if (source.request(MAX_BYTES + 1)) throw IOException("Receipt too large")
                        source.readUtf8()
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    companion object {
        private const val MAX_BYTES = 2L * 1024 * 1024
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .callTimeout(15, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS).build()
    }
}
