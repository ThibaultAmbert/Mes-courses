package fr.mescourses.app.platform

import fr.mescourses.core.retail.Retailer
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIDevice
import platform.WebKit.WKContentWorld
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKProcessPool
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Outils WKWebView partagés : toutes les vues de l'app utilisent le même magasin de données (cookies
 * des drives et de leurs protections anti-robot communs à la vue invisible et à la vue de connexion).
 */
object IosWeb {
    private val processPool = WKProcessPool()

    /** Nouvelle configuration : magasin de données par défaut (persistant), JavaScript, lecture en ligne. */
    fun configuration(): WKWebViewConfiguration = WKWebViewConfiguration().apply {
        setProcessPool(processPool)
        setWebsiteDataStore(WKWebsiteDataStore.defaultDataStore())
        setAllowsInlineMediaPlayback(true)
        defaultWebpagePreferences.setAllowsContentJavaScript(true)
        preferences.setJavaScriptCanOpenWindowsAutomatically(true)
        setUserContentController(WKUserContentController())
    }

    private val iosVersion: String get() = UIDevice.currentDevice.systemVersion

    /** User-Agent de Safari sur iPhone (le WKWebView n'annonce pas « Version/… Safari/… »). */
    val mobileSafari: String
        get() = "Mozilla/5.0 (iPhone; CPU iPhone OS ${iosVersion.replace('.', '_')} like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/${iosVersion.substringBefore('.')}.0 Mobile/15E148 Safari/604.1"

    /** Safari sur Mac : site « ordinateur » de Leclerc, avec un moteur cohérent (WebKit) pour l'anti-robot. */
    val desktopSafari: String
        get() = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
            "Version/${iosVersion.substringBefore('.')}.0 Safari/605.1.15"

    /**
     * User-Agent d'une enseigne, mêmes règles qu'Android adaptées à WebKit : Leclerc « ordinateur » = Safari Mac,
     * sinon Safari iPhone (« webview » garde celui du système).
     */
    fun retailerUserAgent(retailer: Retailer, leclercUaMode: String): String? = when (retailer) {
        Retailer.LECLERC -> when (leclercUaMode) {
            "webview" -> null
            "mobile" -> mobileSafari
            else -> desktopSafari
        }
        else -> mobileSafari
    }

    /**
     * Relève les requêtes `fetch`/`XMLHttpRequest` de la page (adresse + en-têtes posés par le site) : WKWebView
     * ne permet pas d'intercepter le réseau comme la WebView Android. Sert aux jetons de l'API panier Auchan.
     */
    const val REQUEST_HANDLER = "mcRequests"
    private const val REQUEST_HOOK_JS = """(function(){if(window.__mcHook)return;window.__mcHook=1;
function post(u,h){try{window.webkit.messageHandlers.$REQUEST_HANDLER.postMessage({url:String(u),headers:h||{}});}catch(e){}}
function hdrs(x){var o={};if(!x)return o;try{if(typeof Headers!=='undefined'&&x instanceof Headers){x.forEach(function(v,k){o[k]=v;});}
else if(Array.isArray(x)){x.forEach(function(p){o[p[0]]=p[1];});}else{for(var k in x){o[k]=x[k];}}}catch(e){}return o;}
var f=window.fetch;if(f){window.fetch=function(i,init){try{var u=(typeof i==='string')?i:(i&&i.url);var h=hdrs(init&&init.headers);
if(i&&i.headers&&typeof i!=='string'){var hh=hdrs(i.headers);for(var k in hh){if(!(k in h))h[k]=hh[k];}}post(new URL(u,location.href).href,h);}catch(e){}
return f.apply(this,arguments);};}
var op=XMLHttpRequest.prototype.open,sh=XMLHttpRequest.prototype.setRequestHeader,sd=XMLHttpRequest.prototype.send;
XMLHttpRequest.prototype.open=function(m,u){this.__mcUrl=u;this.__mcH={};return op.apply(this,arguments);};
XMLHttpRequest.prototype.setRequestHeader=function(k,v){try{this.__mcH[k]=v;}catch(e){}return sh.apply(this,arguments);};
XMLHttpRequest.prototype.send=function(){try{post(new URL(this.__mcUrl,location.href).href,this.__mcH);}catch(e){}return sd.apply(this,arguments);};})();"""

    /** Installe le relevé des requêtes dans [config] ; [onRequest] reçoit l'adresse et les en-têtes. */
    fun observeRequests(config: WKWebViewConfiguration, onRequest: (String, Map<String, String>) -> Unit) {
        val controller = config.userContentController
        controller.addUserScript(
            WKUserScript(REQUEST_HOOK_JS, WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart, false),
        )
        controller.addScriptMessageHandler(RequestHandler(onRequest), REQUEST_HANDLER)
    }

    private class RequestHandler(private val onRequest: (String, Map<String, String>) -> Unit) : NSObject(), WKScriptMessageHandlerProtocol {
        override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
            val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
            val url = body["url"] as? String ?: return
            val headers = (body["headers"] as? Map<*, *>)?.entries
                ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> key to (v?.toString() ?: "") } }?.toMap().orEmpty()
            runCatching { onRequest(url, headers) }
        }
    }
}

/** Évalue [js] et rend son résultat en texte (null si erreur ou résultat vide). À appeler sur le fil principal. */
suspend fun WKWebView.evalString(js: String): String? = suspendCancellableCoroutine { cont ->
    evaluateJavaScript(js) { result, _ -> if (cont.isActive) cont.resume(result?.toString()) }
}

/**
 * Exécute le corps de fonction asynchrone [body] (qui peut utiliser `await`) dans la page et rend la valeur
 * retournée (texte), ou l'erreur JavaScript.
 */
suspend fun WKWebView.callAsync(body: String): Result<String?> = suspendCancellableCoroutine { cont ->
    callAsyncJavaScript(body, arguments = null, inFrame = null, inContentWorld = WKContentWorld.pageWorld) { result, error: NSError? ->
        if (!cont.isActive) return@callAsyncJavaScript
        if (error != null) cont.resume(Result.failure(IllegalStateException(error.localizedDescription)))
        else cont.resume(Result.success(result?.toString()))
    }
}

/** Chaîne JavaScript littérale (guillemets et échappements JSON). */
fun jsString(s: String): String = Json.encodeToString(String.serializer(), s)

/** Délégué de navigation avec des fonctions simples (le WKWebView ne le retient pas : le garder en référence). */
class NavDelegate(
    var onStart: (url: String?) -> Unit = {},
    var onFinish: (url: String?) -> Unit = {},
    var onFail: (message: String) -> Unit = {},
    /** Faux = navigation refusée (adresse non web : mailto:, intent:…). */
    var allow: (url: String?, mainFrame: Boolean) -> Boolean = { url, _ ->
        url == null || url.startsWith("http") || url.startsWith("about:") || url.startsWith("blob:") || url.startsWith("data:")
    },
) : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol {
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        onStart(webView.URL?.absoluteString)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onFinish(webView.URL?.absoluteString)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        onFail(withError.localizedDescription)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        onFail(withError.localizedDescription)
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString
        val main = decidePolicyForNavigationAction.targetFrame?.mainFrame ?: true
        decisionHandler(
            if (allow(url, main)) WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            else WKNavigationActionPolicy.WKNavigationActionPolicyCancel,
        )
    }

    /** « Nouvelle fenêtre » du site : ouverte dans la même vue (comme sur Android). */
    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? {
        forNavigationAction.request.let { webView.loadRequest(it) }
        return null
    }
}

/** Charge [url] dans la vue (adresse invalide ignorée). */
fun WKWebView.loadUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    loadRequest(platform.Foundation.NSURLRequest.requestWithURL(nsUrl))
}
