package rs.budget.data.receipt

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

object FiscalUrl {
    fun parse(raw: String): HttpUrl {
        require(raw.length in 1..8192 && raw.none { it.isWhitespace() || it == '\\' })
        val uri = URI(raw)
        require(uri.scheme == "https" && uri.host == "suf.purs.gov.rs")
        require(uri.rawUserInfo == null && uri.rawFragment == null && uri.port in listOf(-1, 443))
        require(uri.rawPath == "/v/" || uri.rawPath == "/v")
        val url = requireNotNull(raw.toHttpUrlOrNull())
        require(url.querySize == 1 && url.queryParameterName(0) == "vl")
        require(!url.queryParameterValue(0).isNullOrBlank())
        require(!Regex("%(?![0-9a-fA-F]{2})").containsMatchIn(raw))
        return url
    }
}
