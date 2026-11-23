package fr.mescourses.app.recipes

import fr.mescourses.app.platform.IosWeb
import fr.mescourses.app.platform.NavDelegate
import fr.mescourses.app.platform.evalString
import fr.mescourses.app.platform.loadUrl
import fr.mescourses.core.recipes.PageFetcher
import kotlinx.cinterop.cValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreGraphics.CGRect
import platform.WebKit.WKWebView

/**
 * iOS : charge une page dans un WKWebView invisible (vrai navigateur) puis en lit le HTML. Même rôle et mêmes
 * délais que la version Android : second recours quand un site refuse le client HTTP.
 */
class WkPageFetcher : PageFetcher {
    private val permits = Semaphore(2)

    override suspend fun fetch(url: String): String = permits.withPermit {
        val finished = MutableStateFlow(false)
        val (view, delegate) = withContext(Dispatchers.Main) {
            val v = WKWebView(frame = cValue<CGRect>(), configuration = IosWeb.configuration())
            v.customUserAgent = IosWeb.mobileSafari
            val d = NavDelegate(onFinish = { finished.value = true }, onFail = { finished.value = true })
            v.navigationDelegate = d
            v.loadUrl(url)
            v to d
        }
        try {
            withTimeoutOrNull(LOAD_TIMEOUT_MS) { finished.first { it } }
                ?: throw IllegalStateException("la page ne s'est pas chargée à temps")
            delay(SETTLE_MS)
            withTimeout(CHALLENGE_TIMEOUT_MS) {
                var html = readHtml(view)
                while (isChallenge(html)) {
                    delay(1_000)
                    html = readHtml(view)
                }
                html
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                view.stopLoading()
                view.navigationDelegate = null
                delegate.hashCode()
            }
        }
    }

    private suspend fun readHtml(view: WKWebView): String =
        withContext(Dispatchers.Main) { view.evalString("document.documentElement.outerHTML") }.orEmpty()

    private fun isChallenge(html: String): Boolean =
        CHALLENGE_MARKERS.any { html.contains(it, ignoreCase = true) } && html.length < 200_000

    private companion object {
        const val LOAD_TIMEOUT_MS = 25_000L
        const val SETTLE_MS = 1_200L
        const val CHALLENGE_TIMEOUT_MS = 15_000L
        val CHALLENGE_MARKERS = listOf("challenge-platform", "Just a moment", "captcha-delivery", "cf-chl-")
    }
}
