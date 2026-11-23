package fr.mescourses.app.retail

import fr.mescourses.core.carrefour.HttpTransport
import kotlinx.coroutines.flow.StateFlow

/** Requête passée par le navigateur d'une enseigne (écran de debug). */
data class BridgeLog(val method: String, val route: String, val status: Int?, val millis: Long)

/**
 * Navigateur invisible connecté à une enseigne (Android : WebView ; iOS : WKWebView) : les requêtes du drive
 * sont exécutées par `fetch()` dans la page du site, avec ses cookies et ses protections anti-robot.
 */
interface RetailerBridge : HttpTransport {
    val site: RetailerSite
    val log: StateFlow<List<BridgeLog>>
    val isAttached: Boolean

    /** Recharge la page d'attente de l'enseigne. */
    suspend fun reload()

    /** Recharge la page d'attente et attend la fin du chargement. */
    suspend fun reloadAndWait()

    /** Ouvre [url] et rend la première adresse atteinte qui vérifie [until] (null si délai dépassé). */
    suspend fun visit(url: String, timeoutMs: Long = 25_000, until: (String) -> Boolean): String?

    /** Réapplique le User-Agent (changé dans les Réglages). */
    suspend fun refreshUserAgent()
}
