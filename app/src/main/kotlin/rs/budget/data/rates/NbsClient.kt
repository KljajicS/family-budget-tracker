package rs.budget.data.rates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface RateLookup {
    suspend fun fetch(): NbsRateList
}

class NbsClient internal constructor(
    private val endpoint: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(15, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).build(),
) : RateLookup {
    constructor() : this("https://webappcenter.nbs.rs/ExchangeRateWebApp/ExchangeRate/CurrentMiddleRate")

    override suspend fun fetch(): NbsRateList = withContext(Dispatchers.IO) { NbsParser.parse(read()) }

    private suspend fun read(): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(endpoint).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Rate lookup failed"))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        require(it.isSuccessful) { "Rate lookup failed" }
                        val source = requireNotNull(it.body).source()
                        require(!source.request(256 * 1024L + 1)) { "Rate response too large" }
                        source.readUtf8()
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
}
