@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package fr.mescourses.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import fr.mescourses.app.ui.components.GroupLabel
import fr.mescourses.app.ui.components.NavRow
import fr.mescourses.app.ui.components.RowDivider
import fr.mescourses.app.ui.components.WhiteCard
import fr.mescourses.app.ui.theme.Cuisine
import fr.mescourses.app.ui.components.BusyBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import fr.mescourses.app.ui.components.PillStepper
import fr.mescourses.app.ui.components.SectionCard
import fr.mescourses.app.ui.components.SectionHeader
import fr.mescourses.app.ui.components.StatChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.size
import fr.mescourses.app.AppContainer
import fr.mescourses.app.retail.SessionState
import fr.mescourses.app.ui.retail.RetailerChips
import fr.mescourses.app.ui.retail.RetailerDot
import fr.mescourses.core.retail.Retailer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import fr.mescourses.app.data.Settings
import fr.mescourses.app.llm.DownloadState
import fr.mescourses.app.llm.ModelCatalog
import fr.mescourses.app.llm.ModelInfo
import fr.mescourses.app.llm.ModelStatus
import fr.mescourses.app.ui.common.ScreenScaffold
import fr.mescourses.app.ui.common.containerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import fr.mescourses.app.ui.common.dec
import fr.mescourses.app.ui.common.thousands
import fr.mescourses.app.ui.common.frenchDayTime
import kotlin.math.roundToInt
import fr.mescourses.app.ui.platform.PlatformBackHandler
import fr.mescourses.app.ui.platform.rememberModelFilePicker
import fr.mescourses.app.platform.PickedFile
import fr.mescourses.app.platform.LocalFile
import fr.mescourses.app.platform.UpdateStatus
import kotlinx.coroutines.IO

class SettingsViewModel(val c: AppContainer) : ViewModel() {
    val settings = c.settings.state
    val download = c.models.state
    val installed = MutableStateFlow(c.models.installed())

    /** Charge le modèle en mémoire (le chef devient actif). */
    fun activateChef() { viewModelScope.launch { c.model.refreshAvailability(); c.model.ensureLoaded() } }

    val testResult = MutableStateFlow<String?>(null)

    fun update(transform: (Settings) -> Settings) = viewModelScope.launch { c.settings.update(transform) }

    init {
        // Téléchargement terminé (même lancé depuis une autre visite de l'écran) : liste à jour.
        viewModelScope.launch { c.models.state.collect { if (it is DownloadState.Done) installed.value = c.models.installed() } }
    }

    // Le choix du modèle est enregistré au niveau de l'application (c.selectModel), pas de l'écran :
    // quitter les Réglages pendant le téléchargement ne l'empêche plus.
    fun download(model: ModelInfo) = c.models.download(model, hfToken = settings.value.hfToken) { path -> c.selectModel(path) }

    fun import(file: PickedFile) = c.appScope.launch {
        fr.mescourses.core.runCatchingCancellable { c.models.import(file) }
            .onSuccess { path -> installed.value = c.models.installed(); c.selectModel(path) }
            .onFailure { testResult.value = "Import impossible : ${it.message}" }
    }

    fun delete(file: LocalFile) {
        c.models.delete(file)
        installed.value = c.models.installed()
        if (settings.value.modelPath == file.absolutePath) update { it.copy(modelPath = "") }
    }

    fun session(r: Retailer) = c.session(r).state

    /** Vérifie la session des trois enseignes (leurs WebViews sont chargées le temps de l'écran). */
    fun startChecks() {
        c.checkingRetailers.value = Retailer.entries.toSet()
        Retailer.entries.forEach { r -> viewModelScope.launch { c.session(r).check() } }
    }

    fun stopChecks() {
        c.checkingRetailers.value = emptySet()
    }

    override fun onCleared() = stopChecks()
    fun problem(r: Retailer): String? = c.session(r).lastProblem

    fun logout() = c.logoutAll()
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onLogin: (Retailer) -> Unit, onChooseStore: (Retailer) -> Unit, onDebug: () -> Unit, onOpenCatalog: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it) }
    // État de connexion juste dès l'ouverture, sans attendre une comparaison des prix.
    DisposableEffect(Unit) {
        vm.startChecks()
        onDispose { vm.stopChecks() }
    }
    val s by vm.settings.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val testResult by vm.testResult.collectAsStateWithLifecycle()
    var versionTaps by remember { mutableStateOf(0) }
    val pickModel = rememberModelFilePicker { vm.import(it) }

    var page by rememberSaveable { mutableStateOf<String?>(null) }
    PlatformBackHandler(enabled = page != null) { page = null }
    ScreenScaffold(
        title = PAGES[page] ?: "Réglages",
        onBack = { if (page != null) page = null else onBack() },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val catalog by vm.c.catalogSync.status.collectAsStateWithLifecycle()
            val update by (vm.c.appUpdater?.status ?: remember { MutableStateFlow(UpdateStatus()) }).collectAsStateWithLifecycle()
            val modelStatus by vm.c.model.status.collectAsStateWithLifecycle()
            when (page) {
                null -> {
                    SettingsHome(
                        s = s,
                        modelStatus = modelStatus,
                        catalog = catalog,
                        update = update,
                        canUpdate = vm.c.appUpdater != null,
                        sessions = Retailer.entries.associateWith { r -> vm.session(r).collectAsState().value },
                        onOpen = { page = it },
                        onTheme = { mode -> vm.update { it.copy(theme = mode) } },
                    )

                    testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Text(
                        "Mes courses · version 0.3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable {
                            versionTaps++
                            if (versionTaps >= 7) { versionTaps = 0; onDebug() }
                        }.padding(8.dp),
                    )
                }
                "chef" -> {
                    val freeGb by produceState<Double?>(null, installed) {
                        value = withContext(Dispatchers.IO) { vm.c.models.freeSpaceGb() }
                    }
                    ChefHero(modelStatus, freeGb)
                    SectionCard {

                Text(
                    "Il tourne entièrement sur ton téléphone (LiteRT-LM, GPU Adreno)." + (freeGb?.let { " Espace libre : ${dec(it, 1)} Go." } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (installed.isNotEmpty()) when (val m = modelStatus) {
                    is ModelStatus.Ready -> StatChip("🤖 Chef actif · ${m.modelName} · ${m.backend}")
                    ModelStatus.Loading -> Text("Le chef s'active…", style = MaterialTheme.typography.bodyMedium)
                    is ModelStatus.Failed -> {
                        Text(m.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.activateChef() }) { Text("Réessayer d'activer le chef") }
                    }
                    else -> Button(onClick = { vm.activateChef() }) { Text("Activer le chef") }
                }
                installed.forEach { file ->
                    val active = s.modelPath == file.absolutePath
                    Card(
                        onClick = { vm.update { it.copy(modelPath = file.absolutePath) } },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = active, onClick = { vm.update { it.copy(modelPath = file.absolutePath) } })
                            Column(Modifier.weight(1f)) {
                                Text(file.nameWithoutExtension, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${dec(file.length() / 1e9, 1)} Go" + if (active) " · utilisé" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { vm.delete(file) }) { Text("Supprimer") }
                        }
                    }
                }
                ModelCatalog.models.filter { m -> installed.none { it.name == m.fileName } }.forEach { m ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                StatChip("≈ ${m.approxSizeGb} Go")
                            }
                            Text(m.description, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { vm.download(m) }, enabled = download !is DownloadState.Running) { Text("Télécharger") }
                        }
                    }
                }
                when (val d = download) {
                    is DownloadState.Running -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${d.label} : ${dec(d.downloaded / 1e9, 2)} / ${dec(d.total / 1e9, 2)} Go", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { d.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        )
                        TextButton(onClick = { vm.c.models.cancel() }) { Text("Annuler") }
                    }
                    is DownloadState.Failed -> Text(d.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else -> Unit
                }
                OutlinedButton(onClick = pickModel) { Text("Importer un fichier .litertlm") }
                SwitchRow("Utiliser le GPU", "Recommandé : bien plus rapide", s.preferGpu) { v -> vm.update { it.copy(preferGpu = v) } }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mémoire de conversation (tokens)", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Auto", 4096 to "4 096", 6144 to "6 144", 8192 to "8 192").forEach { (tokens, label) ->
                            InputChip(
                                selected = s.contextTokens == tokens,
                                onClick = { vm.update { it.copy(contextTokens = tokens) } },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Field("Jeton Hugging Face (si le modèle l'exige)", s.hfToken, secret = true) { v -> vm.update { it.copy(hfToken = v) } }
            
                    }
                    GroupLabel("Recherche par le sens")
                    fr.mescourses.app.ui.catalog.SemanticSearchCard()
                }
                "foyer" -> SectionCard {

                PillStepper("🧑", "Adultes par repas", s.householdSize, { n -> vm.update { it.copy(householdSize = n.coerceIn(0, 20)) } })
                PillStepper("🧒", "Enfants par repas", s.children, { n -> vm.update { it.copy(children = n.coerceIn(0, 20)) } })
                PillStepper("🥗", "Entrées par menu", s.starters, { n -> vm.update { it.copy(starters = n.coerceIn(0, 21)) } }, min = 0, max = 21)
                PillStepper("🍽", "Plats par menu", s.meals, { n -> vm.update { it.copy(meals = n.coerceIn(1, 21)) } }, min = 1, max = 21)
                PillStepper("🍰", "Desserts par menu", s.desserts, { n -> vm.update { it.copy(desserts = n.coerceIn(0, 21)) } }, min = 0, max = 21)
                Text("Portion d'un enfant : ${(s.childFactor * 100).toInt()} % d'une portion adulte", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = s.childFactor.toFloat(),
                    onValueChange = { v -> vm.update { it.copy(childFactor = ((v * 20).roundToInt() / 20.0)) } },
                    valueRange = 0.25f..1f,
                    steps = 14,
                )
                Field("Régime, allergies, goûts, budget…", s.preferences, lines = 3) { v -> vm.update { it.copy(preferences = v) } }
            
                }
                "placard" -> SectionCard {

                Text(
                    "Ces produits ne sont jamais ajoutés à la liste de courses depuis une recette. Touche-en un pour le retirer " +
                        "(tu l'achèteras), ou ajoute ce que tu as toujours chez toi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val pantry = s.pantryList()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pantry.forEach { item ->
                        InputChip(
                            selected = false,
                            onClick = { vm.update { it.withPantry(it.pantryList() - item) } },
                            label = { Text(item) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Retirer $item", Modifier.size(16.dp)) },
                        )
                    }
                }
                var newPantry by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPantry,
                        onValueChange = { newPantry = it },
                        label = { Text("Ajouter (ex. riz, café)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val v = newPantry.trim().replaceFirstChar { it.uppercase() }
                            if (v.isNotEmpty()) vm.update { st -> st.withPantry((st.pantryList() + v).distinct()) }
                            newPantry = ""
                        },
                        enabled = newPantry.isNotBlank(),
                    ) { Text("Ajouter") }
                }
                if (s.pantry.isNotEmpty()) TextButton(onClick = { vm.update { it.copy(pantry = "") } }) { Text("Revenir à la liste par défaut") }
            
                }
                "drives" -> SectionCard {

                Text("Enseigne préférée", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Utilisée par défaut pour le panier et l'assistant. Tu peux en changer pour un panier, ou comparer les prix, depuis l'écran Panier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RetailerChips(selected = s.preferredRetailer, preferred = null) { r -> vm.update { it.copy(retailer = r.name) } }
                Retailer.entries.forEach { r ->
                    val session by vm.session(r).collectAsState()
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RetailerDot(r, 32.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(r.label, style = MaterialTheme.typography.bodyLarge)
                            Text(s.storeName(r).ifBlank { r.noStoreText() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatChip(session.label, emphasized = session != SessionState.CONNECTE)
                    }
                    // Raison du dernier refus (ex. « page session expirée du drive 053701 »), pour comprendre sans debug.
                    if (session != SessionState.CONNECTE) vm.problem(r)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // « Drive à choisir » : on est connecté, il manque seulement le drive.
                        if (session != SessionState.CONNECTE && session != SessionState.DRIVE_REQUIS) Button(onClick = { onLogin(r) }) { Text("Se connecter") }
                        TextButton(onClick = { onChooseStore(r) }) { Text(r.chooseStoreText(s.storeName(r).isNotBlank())) }
                    }
                }
                HorizontalDivider()
                SwitchRow("Privilégier le bio", "Propose la version bio quand le drive en a une, sinon le produit classique", s.preferOrganic) { v ->
                    vm.update { it.copy(preferOrganic = v) }
                }
                OutlinedButton(onClick = vm::logout) { Text("Se déconnecter de toutes les enseignes") }
                Text(
                    "Intégrations non officielles, pour un usage personnel. La commande, le créneau et le paiement se font toujours sur le site de l'enseigne.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            
                }
                "bases" -> SectionCard {

                Text(
                    if (catalog.count == 0) "Aucune base téléchargée : Découvrir et le menu cherchent sur les sites."
                    else "${thousands(catalog.count)} recettes sur le téléphone (${catalog.installed.size} base${if (catalog.installed.size > 1) "s" else ""}). Découvrir et le menu y cherchent sans réseau.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (catalog.bySite.isNotEmpty()) Text(
                    catalog.bySite.entries.sortedByDescending { it.value }
                        .joinToString(" · ") { (id, n) -> "${fr.mescourses.core.recipes.RecipeSites.byId(id)?.name ?: id} : ${thousands(n)}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    catalog.working
                        ?: listOfNotNull(
                            catalog.lastUpdate?.let { "Nouveautés cherchées le " + frenchDayTime(it) }
                                ?: "Nouveautés jamais cherchées",
                            catalog.lastResult,
                        ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (catalog.working != null) BusyBar(Modifier.fillMaxWidth())
                Button(onClick = onOpenCatalog) { Text("Choisir les bases à télécharger") }
                OutlinedButton(onClick = { vm.c.catalogSync.updateNow() }, enabled = catalog.working == null && catalog.installed.isNotEmpty()) { Text("Chercher les nouveautés maintenant") }
                Text(
                    "Les bases téléchargées sont d’abord mises à jour depuis le serveur si une version plus récente existe, puis les nouveautés des sites sont cherchées une fois par jour à l’ouverture de l’appli (hors données mobiles).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            
                }
                "maj" -> SectionCard {

                Text(
                    if (update.updateAvailable) "Nouvelle version disponible (build ${update.available}, installée : ${update.installed})."
                    else "Version installée : build ${update.installed}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                (update.working ?: update.message)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (update.working != null) update.progress?.let { p ->
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)))
                } ?: BusyBar(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (update.updateAvailable) Button(onClick = { vm.c.appUpdater?.install() }, enabled = update.working == null) { Text("Mettre à jour") }
                    OutlinedButton(onClick = { vm.c.appUpdater?.checkNow() }, enabled = update.working == null) { Text("Chercher une mise à jour") }
                }
                Text(
                    "Les données (livre de recettes, réglages, modèle) sont conservées. Android demande une confirmation avant d'installer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            
                }
            }

        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


/** Champ texte qui n'enregistre qu'à la perte de focus / validation pour éviter d'écrire à chaque frappe. */
@Composable
private fun Field(label: String, value: String, secret: Boolean = false, number: Boolean = false, lines: Int = 1, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != text) text = value }
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(600)
        if (text != value) onSave(text)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = lines == 1,
        minLines = lines,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Titres des sous-pages des Réglages (null = accueil). */
private val PAGES = mapOf(
    "chef" to "Le chef",
    "foyer" to "Mon foyer",
    "placard" to "Mon placard",
    "drives" to "Mes drives",
    "bases" to "Bases de recettes",
    "maj" to "Mise à jour",
)

private val Ok = Color(0xFF5C9A2E)
private val Warn = Color(0xFFE0A21B)

/** Accueil des Réglages : une ligne par section, avec sa valeur actuelle et un point d'état. */
@Composable
private fun SettingsHome(
    s: fr.mescourses.app.data.Settings,
    modelStatus: ModelStatus,
    catalog: fr.mescourses.app.data.CatalogStatus,
    update: UpdateStatus,
    /** L'app sait se mettre à jour elle-même (Android) ; sur iOS, c'est le système. */
    canUpdate: Boolean = true,
    sessions: Map<Retailer, SessionState>,
    onOpen: (String) -> Unit,
    onTheme: (String) -> Unit,
) {
    fun plural(n: Int, word: String) = "$n $word" + if (n > 1) "s" else ""
    GroupLabel("Cuisine")
    WhiteCard {
        NavRow(
            Icons.Filled.People, Cuisine.SaffronLight, "Mon foyer",
            listOfNotNull(
                plural(s.householdSize, "adulte"),
                s.children.takeIf { it > 0 }?.let { plural(it, "enfant") },
                plural(s.meals, "plat") + " par menu",
            ).joinToString(" · "),
            { onOpen("foyer") },
        )
        RowDivider()
        NavRow(Icons.Filled.Kitchen, Cuisine.Peach, "Toujours dans mon placard", plural(s.pantryList().size, "produit") + " jamais achetés", { onOpen("placard") })
        RowDivider()
        val (chefText, chefDot) = when (modelStatus) {
            is ModelStatus.Ready -> "${modelStatus.modelName} · prêt (${modelStatus.backend})" to Ok
            is ModelStatus.Sleeping -> "${modelStatus.modelName} · en veille" to Ok
            ModelStatus.Loading -> "S'active…" to Warn
            is ModelStatus.Failed -> "Erreur : à réactiver" to MaterialTheme.colorScheme.error
            ModelStatus.Absent -> "À installer" to Warn
        }
        NavRow(Icons.Filled.AutoAwesome, Color(0xFFF6D5C9), "Le chef", chefText + " · recherche par le sens", { onOpen("chef") }, dot = chefDot)
    }
    GroupLabel("Courses")
    WhiteCard {
        val connected = sessions.filterValues { it == SessionState.CONNECTE }.keys
        NavRow(
            Icons.Filled.Storefront, Color(0xFFDDE7F3), "Mes drives",
            "${s.preferredRetailer.label} par défaut · " + when (connected.size) {
                0 -> "aucune enseigne connectée"
                Retailer.entries.size -> "toutes connectées"
                else -> connected.joinToString(", ") { it.label } + " connecté" + if (connected.size > 1) "s" else ""
            } + if (s.preferOrganic) " · bio" else "",
            { onOpen("drives") },
            dot = if (s.preferredRetailer in connected) Ok else Warn,
        )
    }
    GroupLabel("Données")
    WhiteCard {
        NavRow(
            Icons.Filled.Storage, Color(0xFFEDE3F5), "Bases de recettes",
            if (catalog.count == 0) "Aucune base : recherche en ligne"
            else "${thousands(catalog.count)} recettes · ${plural(catalog.installed.size, "base")}",
            { onOpen("bases") },
            dot = if (catalog.working != null) Warn else null,
        )
        if (canUpdate) RowDivider()
        if (canUpdate) NavRow(
            Icons.Filled.SystemUpdate, MaterialTheme.colorScheme.surfaceContainerHigh, "Mise à jour de l'appli",
            if (update.updateAvailable) "Nouvelle version disponible" else "Build ${update.installed} · à jour",
            { onOpen("maj") },
            dot = if (update.updateAvailable) MaterialTheme.colorScheme.primary else null,
        )
    }
    GroupLabel("Apparence")
    WhiteCard {
        Row(
            Modifier.fillMaxWidth().padding(4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf(
                Triple("auto", "Auto", Icons.Filled.BrightnessAuto),
                Triple("jour", "Jour", Icons.Filled.LightMode),
                Triple("nuit", "Nuit", Icons.Filled.DarkMode),
            ).forEach { (mode, label, icon) ->
                fr.mescourses.app.ui.shopping.Segment(label, icon, selected = s.theme == mode, onClick = { onTheme(mode) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Carte d'état du chef en tête de sa sous-page. */
@Composable
private fun ChefHero(status: ModelStatus, freeGb: Double?) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
        Box {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-30).dp).size(120.dp).clip(CircleShape).background(Color(0xFFE2603F)))
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("SUR TON TÉLÉPHONE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Text(
                    when (status) {
                        is ModelStatus.Ready, is ModelStatus.Sleeping -> "Le chef est prêt"
                        ModelStatus.Loading -> "Le chef s'active…"
                        is ModelStatus.Failed -> "Le chef ne démarre pas"
                        ModelStatus.Absent -> "Installe ton chef"
                    },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    "Rien ne quitte l'appareil." + (freeGb?.let { " ${dec(it, 1)} Go libres." } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                )
            }
        }
    }
}
