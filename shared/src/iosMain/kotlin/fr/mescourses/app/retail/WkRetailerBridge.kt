package fr.mescourses.app.retail

import fr.mescourses.app.platform.IosWeb
import fr.mescourses.app.platform.Log
import fr.mescourses.app.platform.NavDelegate
import fr.mescourses.app.platform.callAsync
import fr.mescourses.app.platform.jsString
import fr.mescourses.app.platform.loadUrl
import fr.mescourses.core.auchan.AuchanRoutes
import fr.mescourses.core.carrefour.CarrefourException
import fr.mescourses.core.carrefour.HttpResponse
import fr.mescourses.core.currentTimeMillis
import fr.mescourses.core.retail.Retailer
import fr.mescourses.core.urlHost
import kotlinx.cinterop.cValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.CoreGraphics.CGRect
import platform.WebKit.WKWebView
import kotlin.concurrent.Volatile

/**
 * iOS : exécute les requêtes d'une enseigne par `fetch()` DANS un WKWebView connecté (même rôle que la WebView
 * Android) : la page du site fournit cookies, jetons et passage des protections anti-robot.
 */
class WkRetailerBridge(
    override val site: RetailerSite,
    private val leclercUaMode: () -> String,
    private val onRequestSeen: ((url: String, headers: Map<String, String>) -> Unit)?,
) : RetailerBridge {
    private val who = site.retailer.label
    @Volatile private var webView: WKWebView? = null
    private var delegate: NavDelegate? = null
    @Volatile var landedUrl: String? = null
    @Volatile private var target: String? = null
    private val ready = MutableStateFlow(false)
    private val pageFinished = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val serial = Mutex()
    @Volatile private var lastRequestAt = 0L

    private val _log = MutableStateFlow<List<BridgeLog>>(emptyList())
    override val log: StateFlow<List<BridgeLog>> = _log
    override val isAttached: Boolean get() = webView != null

    /** User-Agent annoncé (null = celui du système). Partagé avec la vue de connexion visible. */
    fun userAgent(): String? = IosWeb.retailerUserAgent(site.retailer, leclercUaMode())

    /** Relève les requêtes de la page (Auchan : jetons). Aussi appelé par la vue visible. */
    fun observe(url: String, headers: Map<String, String>) {
        onRequestSeen?.let { runCatching { it(url, headers) } }
    }

    /** Vue invisible, créée une fois (à appeler sur le fil principal) et attachée à l'écran par l'interface. */
    fun view(): WKWebView = webView ?: run {
        val config = IosWeb.configuration()
        if (onRequestSeen != null) IosWeb.observeRequests(config) { u, h -> observe(u, h) }
        val view = WKWebView(frame = cValue<CGRect>(), configuration = config)
        userAgent()?.let { view.customUserAgent = it }
        val d = NavDelegate(
            onStart = { ready.value = false },
            onFinish = { url ->
                ready.value = url?.startsWith("http") == true
                landedUrl = url
                url?.let { pageFinished.tryEmit(it) }
            },
            allow = { url, _ ->
                url?.let { if (onRequestSeen != null && it.startsWith("http")) observe(it, emptyMap()) }
                url == null || url.startsWith("http") || url.startsWith("about:") || url.startsWith("blob:")
            },
        )
        delegate = d
        view.navigationDelegate = d
        view.UIDelegate = d
        webView = view
        val start = site.parking()
        target = start
        view.loadUrl(start)
        view
    }

    override suspend fun refreshUserAgent() = withContext(Dispatchers.Main) {
        webView?.let { it.customUserAgent = userAgent(); it.reload() }
        Unit
    }

    private suspend fun load(url: String) = withContext(Dispatchers.Main) {
        target = url
        ready.value = false
        webView?.loadUrl(url)
    }

    override suspend fun reload() {
        load(site.parking())
    }

    override suspend fun reloadAndWait(): Unit = serial.withLock {
        if (webView == null) return@withLock
        val page = site.parking()
        coroutineScope {
            val done = async { withTimeoutOrNull(20_000) { pageFinished.first { it.startsWith("http") } } }
            load(page)
            done.await()
        }
    }

    override suspend fun visit(url: String, timeoutMs: Long, until: (String) -> Boolean): String? = serial.withLock {
        if (webView == null) return@withLock null
        val landed = coroutineScope {
            val landing = async { withTimeoutOrNull(timeoutMs) { pageFinished.first(until) } }
            load(url)
            landing.await()
        }
        load(site.parking())
        landed
    }

    private suspend fun awaitReady(): WKWebView {
        val ok = withTimeoutOrNull(20_000) { ready.first { it } }
        return webView?.takeIf { ok != null }
            ?: throw CarrefourException.Network("page $who non chargée (application en arrière-plan ou hors connexion)", who)
    }

    override suspend fun request(method: String, path: String, headers: Map<String, String>, body: String?): HttpResponse =
        serial.withLock {
            val wait = lastRequestAt + MIN_INTERVAL_MS - currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRequestAt = currentTimeMillis()
            val page = if (path.startsWith("http")) site.pageFor(path) else site.parking()
            // La vue peut être en cours de création (enseigne qu'on vient d'ajouter à la comparaison).
            val attached = withTimeoutOrNull(20_000) { while (webView == null) delay(100); true }
            if (attached != null && page != target) load(page)
            val view = awaitReady()
            val start = currentTimeMillis()
            val credentials = headers[AuchanRoutes.CREDENTIALS_HEADER] ?: "include"
            val sent = headers - AuchanRoutes.CREDENTIALS_HEADER
            val js = buildString {
                append("var o={method:").append(jsString(method))
                append(",headers:").append(Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), sent))
                append(",credentials:").append(jsString(credentials)).append("};")
                if (body != null) append("o.body=").append(jsString(body)).append(";")
                append("var r=await fetch(").append(jsString(path)).append(",o);var h={};r.headers.forEach(function(v,k){h[k]=v;});")
                append("var t=await r.text();return JSON.stringify({status:r.status,headers:h,body:t});")
            }
            try {
                val raw = withTimeout(TIMEOUT_MS) { withContext(Dispatchers.Main) { view.callAsync(js) } }.getOrThrow()
                    ?: throw IllegalStateException("réponse vide")
                val obj = Json.parseToJsonElement(raw).jsonObject
                val response = HttpResponse(
                    status = obj.getValue("status").jsonPrimitive.int,
                    headers = obj["headers"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
                    body = obj["body"]?.jsonPrimitive?.content.orEmpty(),
                )
                record(method, path, response.status, currentTimeMillis() - start)
                response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                record(method, path, null, currentTimeMillis() - start)
                if (e is CarrefourException) throw e
                val msg = e.message ?: e.toString()
                // « Load failed » : requête bloquée par le navigateur (la page n'est pas sur le bon site, ou CORS).
                val where = landedUrl?.let(::urlHost)
                throw CarrefourException.Network(if (where != null) "$msg (page de départ : $where)" else msg, who)
            }
        }

    private fun record(method: String, path: String, status: Int?, millis: Long) {
        val route = path.substringBefore('?')
        Log.d(TAG, "${site.retailer} $method $route -> ${status ?: "échec"} ($millis ms)")
        _log.value = (listOf(BridgeLog(method, route, status, millis)) + _log.value).take(50)
    }

    private companion object {
        const val TAG = "RetailerBridge"
        const val MIN_INTERVAL_MS = 400L
        const val TIMEOUT_MS = 15_000L
    }
}
