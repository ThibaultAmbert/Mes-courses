package fr.mescourses.core.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Appels d'outils décrits dans le prompt, pour un moteur qui ne sait pas recevoir les outils de l'app
 * (API Swift de LiteRT-LM) : le modèle répond par un objet JSON `{"outil": …, "arguments": {…}}` quand il
 * veut appeler un outil, et reçoit le résultat dans le message suivant.
 */
object PromptTools {
    data class Call(val name: String, val arguments: JsonObject)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Consigne ajoutée au prompt système : liste des outils (description OpenAPI) et forme d'un appel. */
    fun instructions(toolDescriptions: List<String>): String = buildString {
        appendLine()
        appendLine()
        appendLine("## Outils")
        appendLine(
            "Tu peux appeler les outils ci-dessous. Pour en appeler un, réponds UNIQUEMENT par un objet JSON sur une " +
                "seule ligne, sans aucun autre texte : {\"outil\": \"nom_de_l_outil\", \"arguments\": {…}}. " +
                "Tu reçois ensuite le résultat dans un message qui commence par « $RESULT_PREFIX », puis tu continues " +
                "(autre appel d'outil, ou réponse à l'utilisateur). Quand tu réponds à l'utilisateur, écris du texte " +
                "normal, jamais de JSON.",
        )
        toolDescriptions.forEach { appendLine("- $it") }
    }

    /** Message qui rend au modèle le résultat d'un outil. */
    fun resultMessage(name: String, result: String): String = "$RESULT_PREFIX $name : $result"

    const val RESULT_PREFIX = "Résultat de l'outil"

    /**
     * Le début de réponse [text] peut-il encore être un appel d'outil ? Vrai tant qu'il est vide ou commence
     * par `{` ou par un bloc de code : on attend la suite avant de l'afficher.
     */
    fun mayBeCall(text: String): Boolean {
        val t = text.trimStart()
        return t.isEmpty() || t.startsWith("{") || "```".startsWith(t.take(3)) && (t.length < 3 || t.startsWith("```"))
    }

    /** Appel d'outil contenu dans la réponse [text] (tout le texte doit être l'appel), ou null. */
    fun parse(text: String): Call? {
        var t = text.trim()
        if (t.startsWith("```")) t = t.removePrefix("```").removePrefix("json").removePrefix("JSON").substringBeforeLast("```").trim()
        if (!t.startsWith("{") || !t.endsWith("}")) return null
        val obj = runCatching { json.parseToJsonElement(t).jsonObject }.getOrNull() ?: return null
        val name = listOf("outil", "tool", "name", "nom").firstNotNullOfOrNull { (obj[it] as? JsonPrimitive)?.content }
            ?.takeIf { it.isNotBlank() } ?: return null
        val args = listOf("arguments", "args", "parameters", "parametres", "paramètres")
            .firstNotNullOfOrNull { obj[it] as? JsonObject } ?: JsonObject(emptyMap())
        return Call(name, args)
    }
}
