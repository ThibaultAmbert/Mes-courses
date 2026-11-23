package fr.mescourses.app.platform

import kotlinx.coroutines.flow.StateFlow

/** État de la mise à jour de l'appli, affiché dans les Réglages. */
data class UpdateStatus(
    val installed: Long = 0,
    /** Dernière version publiée (numéro de build) ; null = pas encore cherchée. */
    val available: Long? = null,
    val working: String? = null,
    /** Avancement du téléchargement (0..1) ; null = inconnu. */
    val progress: Float? = null,
    val message: String? = null,
    /** Chemin du paquet téléchargé, prêt à installer. */
    val ready: String? = null,
) {
    val updateAvailable get() = available != null && available > installed
}

/** Mise à jour de l'app depuis elle-même (Android : APK de la release « appli »). */
interface AppUpdates {
    val status: StateFlow<UpdateStatus>
    fun start()
    fun checkNow()
    fun install()
}
