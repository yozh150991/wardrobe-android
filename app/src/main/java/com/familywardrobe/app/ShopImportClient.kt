package com.familywardrobe.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

/** Public HTTPS only: no WebView, script execution, login, cookies or service credentials. */
internal object ShopImportClient {
    /** Local, private, shared, documentation, benchmark and reserved ranges are never fetched. */
    private fun nonPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return true
        val b = address.address.map { it.toInt() and 255 }
        return if (b.size == 4) b[0] == 0 || b[0] >= 240 || (b[0] == 100 && b[1] in 64..127) ||
            (b[0] == 192 && b[1] == 0 && b[2] in setOf(0, 2)) || (b[0] == 198 && b[1] in 18..19) ||
            (b[0] == 198 && b[1] == 51 && b[2] == 100) || (b[0] == 203 && b[1] == 0 && b[2] == 113)
        else (b[0] and 0xfe) == 0xfc || (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8) ||
            (b[0] == 0 && b[1] == 0x64 && b[2] == 0xff && b[3] == 0x9b)
    }
    private data class Response(val url: String, val bytes: ByteArray, val contentType: String)
    private suspend fun get(input: String, max: Int, image: Boolean): Response = withContext(Dispatchers.IO) {
        var url = input
        val deadline = System.nanoTime() + 45_000_000_000L
        repeat(5) {
            coroutineContext.ensureActive()
            require(System.nanoTime() < deadline) { "Download deadline exceeded" }
            require(ShopImportRules.webUrl(url).isNotBlank())
            val target = URL(url)
            require(target.protocol.equals("https", true) && target.port in setOf(-1, 443))
            val addresses = InetAddress.getAllByName(target.host)
            require(addresses.isNotEmpty() && addresses.none(::nonPublic))
            val connection = target.openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                connection.useCaches = false
                connection.setRequestProperty("Accept", if (image) "image/*" else "text/html,application/xhtml+xml")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("User-Agent", "Wardrobe/0.12 ProductImport")
                val status = connection.responseCode
                coroutineContext.ensureActive()
                if (status in setOf(301, 302, 303, 307, 308)) {
                    url = ShopImportRules.resolve(url, connection.getHeaderField("Location").orEmpty())
                    require(url.isNotBlank())
                } else {
                    require(status in 200..299)
                    val type = connection.contentType.orEmpty()
                    require(if (image) type.startsWith("image/", true) else type.startsWith("text/html", true) || type.startsWith("application/xhtml+xml", true))
                    require(connection.contentLengthLong <= max)
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { stream ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            require(System.nanoTime() < deadline) { "Download deadline exceeded" }
                            val count = stream.read(buffer); if (count < 0) break
                            require(output.size() + count <= max)
                            output.write(buffer, 0, count)
                        }
                    }
                    return@withContext Response(url, output.toByteArray(), type)
                }
            } finally { connection.disconnect() }
        }
        throw IllegalArgumentException("Too many redirects")
    }
    suspend fun metadata(url: String): List<ShopMetadata.Product> {
        val page = get(url, ShopMetadata.MAX_HTML, false)
        val name = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(page.contentType)?.groupValues?.get(1)
        val charset = runCatching { name?.let(java.nio.charset.Charset::forName) }.getOrNull() ?: Charsets.UTF_8
        return withContext(Dispatchers.Default) { ShopMetadata.parse(page.bytes.toString(charset), page.url) }
    }
    suspend fun image(url: String, directory: File): File {
        val response = get(url, 16 * 1024 * 1024, true)
        var created: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val file = File(directory, "shop-${newId()}.tmp")
                created = file
                try { coroutineContext.ensureActive(); file.writeBytes(response.bytes); coroutineContext.ensureActive(); file }
                catch (e: Exception) { file.delete(); throw e }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { created?.delete() }
            throw e
        }
    }
}
