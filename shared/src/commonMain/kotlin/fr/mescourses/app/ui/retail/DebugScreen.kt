package fr.mescourses.app.ui.retail

import fr.mescourses.app.ui.components.BusyBar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.LinearProgressIndicator
import fr.mescourses.core.retail.DebugReader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mescourses.app.ui.common.ScreenScaffold
import fr.mescourses.app.ui.common.appContainer
import fr.mescourses.app.ui.components.SectionCard
import fr.mescourses.core.auchan.AuchanRoutes
import fr.mescourses.core.carrefour.CarrefourRoutes
import fr.mescourses.core.leclerc.LeclercRoutes
import fr.mescourses.core.retail.Retailer
import fr.mescourses.core.carrefour.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.IO
import okio.Path.Companion.toPath
import okio.FileSystem
import fr.mescourses.app.platform.LocalFile

private val EMAIL_RE = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
private val PHONE_RE = Regex("""(?<!\d)(?:\+33|0)[1-9](?:[ .-]?\d{2}){4}(?!\d)""")
private val BEARER_RE = Regex("""Bearer\s+[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE)
private val PII_JSON_RE = Regex(
    """"(firstName|lastName|firstname|lastname|fullName|recipientName|name_on_card|customerName|email|phone|mobile|address|address1|address2|street|streetAddress|postalCode|zipCode|city|birthDate|iban|cardNumber|token|access_token|refresh_token|authorization|cookie)"\s*:\s*"[^"]*"""",
    RegexOption.IGNORE_CASE,
)

/** Masque e-mails, téléphones, jetons, noms et adresses avant export d'une réponse. */
fun anonymize(text: String): String = text
    .replace(EMAIL_RE, "anonyme@exemple.invalid")
    .replace(PHONE_RE, "0600000000")
    .replace(BEARER_RE, "Bearer ***")
    .replace(PII_JSON_RE, "\"$1\":\"***\"")

/** Écran de test des requêtes des enseignes (accès caché : 7 appuis sur la version, dans Réglages). */
@Composable
fun RetailerDebugScreen(onBack: () -> Unit, onOpenSite: (Retailer) -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var retailer by remember { mutableStateOf(c.activeRetailer.value) }
    var sending by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    // La WebView de l'enseigne testée doit être chargée, même si ce n'est pas l'enseigne active.
    DisposableEffect(retailer) {
        c.debugRetailer.value = retailer
        onDispose { c.debugRetailer.value = null }
    }
    val log by c.bridge(retailer).log.collectAsStateWithLifecycle()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    var method by remember { mutableStateOf("GET") }
    val presets = when (retailer) {
        Retailer.CARREFOUR -> listOf(
            "Panier" to CarrefourRoutes.CART,
            "Recherche lait" to CarrefourRoutes.search("lait"),
            "Drive favori" to CarrefourRoutes.FAVORITE_STORE,
            "Session" to CarrefourRoutes.SESSION_PROBE,
            "Simulation panier" to CarrefourRoutes.cartSimulate(
                settings.storeRef.ifBlank { settings.basketServiceId.substringBefore('-') }.ifBlank { "0" },
            ),
        )
        Retailer.LECLERC -> settings.leclercStore()?.let { s ->
            listOf("Panier (page)" to LeclercRoutes.cartPage(s), "Recherche lait" to LeclercRoutes.search(s, "lait"), "Recherche lardons" to LeclercRoutes.search(s, "lardons"))
        } ?: listOf("Magasins 37700" to LeclercRoutes.locatorAutocomplete("37700"))
        Retailer.AUCHAN -> listOfNotNull(
            "Recherche lait" to AuchanRoutes.search("lait"),
            c.auchanTokens.current()?.consentId?.let { "Panier (API)" to AuchanRoutes.cartMine(it) },
        )
    }
    var path by remember(retailer) { mutableStateOf(presets.first().second) }
    var body by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<HttpResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var millis by remember { mutableStateOf(0L) }
    var driveInfo by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Debug ${retailer.label}", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Retailer.entries.forEach { r -> FilterChip(selected = retailer == r, onClick = { retailer = r; result = null; error = null; driveInfo = null }, label = { Text(r.label) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { (label, p) -> FilterChip(selected = path == p, onClick = { method = "GET"; path = p; body = "" }, label = { Text(label) }) }
                // Leclerc : ajout au panier tel que l'app l'envoie (remplacer ID_PRODUIT par un iIdProduit lu dans une recherche).
                if (retailer == Retailer.LECLERC) settings.leclercStore()?.let { st ->
                    val op = LeclercRoutes.cartOp(st)
                    FilterChip(selected = path == op, onClick = {
                        method = "POST"; path = op
                        body = "{\"eTypeAction\":${LeclercRoutes.ACTION_ADD},\"iIdProduit\":\"ID_PRODUIT\",\"iQuantite\":1,\"sNoPointLivraison\":\"${st.id}\"}"
                    }, label = { Text("Ajout panier (POST)") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("GET", "POST", "PATCH").forEach { m -> FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) }) }
            }
            OutlinedTextField(path, { path = it }, label = { Text("Chemin") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            if (method != "GET") {
                OutlinedTextField(body, { body = it }, label = { Text("Corps JSON") }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
            Button(enabled = !sending, onClick = {
                scope.launch {
                    error = null; result = null; summary = null; sending = true
                    val start = fr.mescourses.core.currentTimeMillis()
                    try {
                        result = if (retailer == Retailer.CARREFOUR) {
                            c.carrefour.call(method, path, body.ifBlank { null })
                        } else {
                            // Requête brute par la WebView de l'enseigne (en-têtes de l'API panier pour Auchan).
                            // Leclerc, écriture du panier : formulaire « d=<json> », comme l'app.
                            val leclercForm = retailer == Retailer.LECLERC && "panier.aspx" in path && body.trimStart().startsWith("{")
                            val headers = when {
                                path.startsWith(AuchanRoutes.API) -> mapOf("accept" to "application/json") + (c.auchanTokens.current()?.headers ?: emptyMap())
                                leclercForm -> LeclercRoutes.FORM_HEADERS
                                else -> mapOf("accept" to "application/json, text/html")
                            }
                            val sent = if (leclercForm) LeclercRoutes.formBody(body.trim()) else body.ifBlank { null }
                            c.bridge(retailer).request(method, path, headers, sent)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = "${e::class.simpleName} : ${e.message}"
                    }
                    millis = fr.mescourses.core.currentTimeMillis() - start
                    sending = false
                }
            }) { Text(if (sending) "Envoi… (chargement de ${retailer.site})" else "Envoyer") }
            if (sending) BusyBar(Modifier.fillMaxWidth())

            // Identifiants du drive seulement (aucune donnée personnelle).
            if (retailer == Retailer.LECLERC) Text(
                settings.leclercStore()?.let { "Drive : ${it.id}-${it.noPR} sur ${it.host}" } ?: "Aucun drive Leclerc choisi.",
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            )
            if (retailer == Retailer.AUCHAN) {
                // On dit seulement si les jetons ont été vus, jamais leur valeur.
                val t = c.auchanTokens.current()
                Text(
                    "Jetons API relevés : ${t?.headers?.keys?.sorted()?.joinToString().orEmpty().ifBlank { "aucun" }}\n" +
                        "consentId : ${if (t?.consentId != null) "oui" else "non"}\nMagasin lu : ${settings.auchanStoreName.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                )
            }
            if (retailer == Retailer.CARREFOUR) OutlinedButton(onClick = {
                scope.launch {
                    driveInfo = "Recherche…"
                    val ctx = c.carrefour.discoverContext()
                    driveInfo = if (ctx == null) {
                        "Aucun identifiant de drive trouvé (panier vide et aucune offre dans la recherche)."
                    } else {
                        "basketServiceId : ${ctx.basketServiceId}\nsubBasketType : ${ctx.subBasketType}\n" +
                            "facilityServiceId : ${ctx.facilityServiceId ?: "—"}\nsource : ${c.carrefour.contextSource ?: "?"}"
                    }
                }
            }) { Text("Contexte drive") }
            driveInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            result?.let { r ->
                SectionCard {
                    Text("Statut ${r.status} · $millis ms · ${r.body.length} caractères", style = MaterialTheme.typography.titleSmall)
                    SelectionContainer {
                        Text(r.body.take(3000), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { summary = DebugReader.summarize(retailer, r.body) }) { Text("Lire avec l'app") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                // Réponse complète dans un fichier : écrite hors UI et nettoyée pour limiter le cache.
                                val text = "${retailer.label} $method ${path.substringBefore('?')}\nstatut ${r.status}\n\n" + anonymize(r.body)
                                val file = withContext(Dispatchers.IO) {
                                    val dir = LocalFile(LocalFile(c.platform.cacheDir), "debug").mkdirs()
                                    // Les noms finissent par l'heure : les plus récents sont en tête une fois triés.
                                    dir.listFiles().sortedByDescending { it.name }.drop(4).forEach { it.delete() }
                                    LocalFile(dir, "${retailer.name.lowercase()}-${fr.mescourses.core.currentTimeMillis()}.txt").also { f ->
                                        FileSystem.SYSTEM.write(f.path.toPath()) { writeUtf8(text) }
                                    }
                                }
                                c.platform.shareFile(file.path, "text/plain", "Exporter la réponse (anonymisée)")
                            }
                        }) { Text("Exporter (fichier)") }
                    }
                    summary?.let { sm ->
                        SelectionContainer { Text(sm, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    }
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Se présenter comme Chrome", style = MaterialTheme.typography.bodyLarge)
                        Text("Retire « ; wv » du User-Agent si Cloudflare bloque la WebView (redémarrer l'app).", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = settings.uaWithoutWv, onCheckedChange = { v -> scope.launch { c.settings.update { it.copy(uaWithoutWv = v) } } })
                }
                OutlinedButton(onClick = { onOpenSite(retailer) }) { Text("Ouvrir ${retailer.site} (WebView visible)") }
            }

            if (log.isNotEmpty()) {
                SectionCard {
                    Text("Journal (sans cookies ni contenu)", style = MaterialTheme.typography.titleSmall)
                    log.take(20).forEach { l ->
                        Text("${l.method} ${l.route} → ${l.status ?: "échec"} (${l.millis} ms)", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
