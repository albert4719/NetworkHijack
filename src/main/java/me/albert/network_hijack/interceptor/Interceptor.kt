package me.albert.network_hijack.interceptor

import me.albert.network_hijack.NetworkHijack
import me.albert.network_hijack.instance
import me.albert.network_hijack.monitorConfig
import me.albert.network_hijack.monitorPluginName
import org.bukkit.configuration.file.YamlConfiguration
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.cert.Certificate
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext


class NetworkInterceptorFactory(private val plugin: NetworkHijack) : URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
        return if (protocol.equals("http", true) || protocol.equals("https", true)) {
            NetworkInterceptor(plugin)
        } else {
            null
        }
    }
}

fun URL.cacheKey() = this.toString().substringBefore("?").replace('.', '_').lowercase()

class NetworkInterceptor(private val plugin: NetworkHijack) : URLStreamHandler() {
    override fun openConnection(url: URL): HttpURLConnection {
        val callingPlugin = plugin.getCallingPlugin()
        val pluginName = callingPlugin?.name ?: "Unknown"

        return HttpsURLConnectionWrapper(url, plugin, pluginName)
    }

    // 自定义 HttpURLConnection 包装类
    class HttpsURLConnectionWrapper(
        url: URL,
        private val plugin: NetworkHijack,
        private val pluginName: String
    ) : HttpsURLConnection(url) {
        private var responseBody: String? = null
        private var responseCode: Int = -1
        private var responseHeaders: HttpHeaders? = null
        private val outputStream = ByteArrayOutputStream()
        private var connected = false
        private var cipherSuite: String? = null
        private var localCertificates: Array<Certificate>? = null
        private var serverCertificates: Array<Certificate>? = null
        private val client: HttpClient
        var bodyText = ""

        init {
            // 初始化 HttpClient，设置 SSLContext
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, null)
            client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build()
        }

        override fun getOutputStream(): OutputStream {
            return outputStream
        }

        override fun connect() {
            if (connected) return
            connected = true

            val requestBuilder = HttpRequest.newBuilder()
                .uri(url.toURI())

            val method = this.method
            val bodyData = outputStream.toByteArray()
            bodyText = if (bodyData.size < 1024 * 1024) bodyData.decodeToString().take(2000) else ""
            val body = HttpRequest.BodyPublishers.ofByteArray(outputStream.toByteArray())
            requestBuilder.method(method.uppercase(), body)
            requestProperties.forEach { (key, values) ->
                values.forEach { value -> requestBuilder.header(key, value) }
            }

            val request = requestBuilder.build()
            try {
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())

                // 获取 SSLSession 信息
                val sslSession = response.sslSession().orElse(null)
                cipherSuite = sslSession?.cipherSuite ?: "UNKNOWN"
                localCertificates = sslSession?.localCertificates ?: emptyArray()
                serverCertificates = sslSession?.peerCertificates ?: emptyArray()

                responseBody = response.body()
                responseCode = response.statusCode()
                responseHeaders = response.headers()
            } catch (e: Exception) {

            }
        }

        override fun getResponseCode(): Int {
            if (!connected) connect()
            return responseCode
        }

        override fun getInputStream(): InputStream {
            if (!connected) connect()
            var stream = responseBody?.byteInputStream() ?: ByteArrayInputStream(ByteArray(0))
            val whitelist = instance.config.getStringList("whitelist")
            if (whitelist.any { url.toString().startsWith(it) }) {
                return stream
            }
            if (!monitorPluginName.contentEquals(pluginName, true)) {
                val filePath = "data${File.separatorChar}${pluginName.lowercase()}.yml"
                val configFile = File(instance.dataFolder, filePath)
                if (configFile.exists()) {
                    val config = YamlConfiguration.loadConfiguration(configFile)
                    val dataCached = config.getString(url.cacheKey()) ?: return stream
                    if (dataCached.isNotEmpty()) {
                        instance.log("已成功拦截并替换插件${pluginName}请求${url}")
                        val dataBytes = Base64.getDecoder().decode(dataCached)
                        return ByteArrayInputStream(dataBytes)
                    }
                }
                return stream
            }
            val config = monitorConfig?.config ?: return stream
            val origBytes = stream.readBytes()
            val text: String = origBytes.decodeToString()
            stream = ByteArrayInputStream(origBytes)
            val base64Data = Base64.getEncoder().encodeToString(text.toByteArray())
            config.set(url.cacheKey(), base64Data)

            plugin.log(
                "检测到网络请求成功${method}: URL=$url, 请求体: ${bodyText} 响应体: ${
                    text.take(
                        10000
                    )
                }"
            )

            return stream
        }

        override fun getHeaderField(name: String?): String? {
            if (!connected) connect()
            return responseHeaders?.firstValue(name ?: "")?.orElse(null)
        }

        override fun getHeaderFields(): Map<String, List<String>> {
            if (!connected) connect()
            return responseHeaders?.map() ?: emptyMap()
        }

        override fun disconnect() {
            connected = false
        }

        override fun usingProxy(): Boolean = false

        override fun getCipherSuite(): String {
            if (!connected) connect()
            return cipherSuite ?: throw IllegalStateException("Cipher suite not available")
        }

        override fun getLocalCertificates(): Array<Certificate> {
            if (!connected) connect()
            return localCertificates ?: throw IllegalStateException("Local certificates not available")
        }

        override fun getServerCertificates(): Array<Certificate> {
            if (!connected) connect()
            return serverCertificates ?: throw IllegalStateException("Server certificates not available")
        }
    }
}