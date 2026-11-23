package fr.mescourses.app.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.distantPast
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKHTTPCookieStoreObserverProtocol
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import kotlin.concurrent.Volatile

/**
 * Cookies des WKWebView (magasin par défaut, commun à toutes les vues de l'app). WKHTTPCookieStore ne se lit
 * qu'en asynchrone : on garde une copie (domaine, nom) tenue à jour par l'observateur du magasin.
 */
class IosCookieJar(private val scope: CoroutineScope) : CookieJar {
    private data class Key(val domain: String, val name: String)

    @Volatile private var snapshot: List<Key> = emptyList()
    private val store: WKHTTPCookieStore get() = WKWebsiteDataStore.defaultDataStore().httpCookieStore

    private val observer = object : NSObject(), WKHTTPCookieStoreObserverProtocol {
        override fun cookiesDidChangeInCookieStore(cookieStore: WKHTTPCookieStore) = refresh()
    }

    init {
        scope.launch(Dispatchers.Main) {
            store.addObserver(observer)
            refresh()
        }
    }

    private fun refresh() {
        store.getAllCookies { cookies ->
            snapshot = cookies.orEmpty().mapNotNull { c ->
                (c as? NSHTTPCookie)?.let { Key(it.domain.trimStart('.').lowercase(), it.name) }
            }
        }
    }

    override fun has(url: String, name: String): Boolean {
        val host = NSURL.URLWithString(url)?.host?.lowercase() ?: return false
        return snapshot.any { it.name == name && (host == it.domain || host.endsWith("." + it.domain)) }
    }

    /** WKWebView enregistre ses cookies tout seul ; on rafraîchit seulement la copie. */
    override fun flush() {
        scope.launch(Dispatchers.Main) { refresh() }
    }

    override fun clearAll() {
        scope.launch(Dispatchers.Main) {
            WKWebsiteDataStore.defaultDataStore().removeDataOfTypes(
                WKWebsiteDataStore.allWebsiteDataTypes(),
                modifiedSince = NSDate.distantPast,
            ) { refresh() }
        }
    }
}
