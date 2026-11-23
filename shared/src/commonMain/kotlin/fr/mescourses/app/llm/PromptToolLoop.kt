package fr.mescourses.app.llm

import fr.mescourses.core.agent.AgentTool
import fr.mescourses.core.llm.PromptTools
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Boucle d'outils menée par l'app pour un moteur sans outils natifs (iOS) : le modèle écrit un appel en JSON,
 * l'app exécute l'outil, renvoie le résultat, et ainsi de suite. Mêmes événements que les outils natifs d'Android.
 */
object PromptToolLoop {
    /** Appels d'outils successifs au plus, pour un message de l'utilisateur. */
    private const val MAX_STEPS = 8

    fun systemPrompt(base: String, tools: List<AgentTool>): String =
        if (tools.isEmpty()) base else base + PromptTools.instructions(tools.map { it.openApiJson() })

    /**
     * Envoie [message] puis enchaîne les appels d'outils. [rawTurn] : un tour du moteur (morceaux de texte).
     * Le texte n'est montré que quand ce n'est pas un appel d'outil.
     */
    fun run(message: String, tools: () -> Map<String, AgentTool>, rawTurn: (String) -> Flow<String>): Flow<ChatEvent> = flow {
        var input = message
        for (step in 0 until MAX_STEPS) {
            val held = StringBuilder()
            var streaming = false
            rawTurn(input).collect { chunk ->
                if (streaming) {
                    emit(ChatEvent.Token(chunk))
                } else {
                    held.append(chunk)
                    if (!PromptTools.mayBeCall(held.toString())) {
                        streaming = true
                        emit(ChatEvent.Token(held.toString()))
                    }
                }
            }
            if (streaming) return@flow
            val call = PromptTools.parse(held.toString())
            val tool = call?.let { tools()[it.name] }
            if (call == null || tool == null) {
                if (held.isNotBlank()) emit(ChatEvent.Token(held.toString()))
                return@flow
            }
            emit(ChatEvent.ToolCall(call.name, call.arguments.toString()))
            val result = runCatching { tool.execute(call.arguments) }
                .getOrElse { e -> JsonObject(mapOf("ok" to kotlinx.serialization.json.JsonPrimitive(false), "erreur" to kotlinx.serialization.json.JsonPrimitive(e.message ?: e.toString()))) }
            val obj = result as? JsonObject
            val ok = obj?.get("ok")?.jsonPrimitive?.content != "false"
            val summary = (obj?.get("message") ?: obj?.get("erreur"))?.jsonPrimitive?.content.orEmpty()
            val text = result.toString()
            emit(ChatEvent.ToolResult(call.name, ok, summary, size = text.length))
            input = PromptTools.resultMessage(call.name, text)
        }
    }
}
