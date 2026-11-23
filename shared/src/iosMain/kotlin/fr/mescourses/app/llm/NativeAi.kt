package fr.mescourses.app.llm

import fr.mescourses.app.platform.AppTextEmbedder
import fr.mescourses.app.platform.LocalFile
import fr.mescourses.app.platform.Log
import fr.mescourses.core.agent.AgentTool
import fr.mescourses.core.catalog.EmbeddingText
import fr.mescourses.core.catalog.Int8Vectors
import fr.mescourses.core.llm.Msg
import fr.mescourses.core.llm.MsgRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * LLM et embeddings sur iPhone : le moteur (LiteRT-LM, paquet Swift) est écrit en Swift dans iosApp et fourni à
 * MainViewController. Ces interfaces à rappels simples sont celles que le code Swift implémente ; IosLlmEngine et
 * IosTextEmbedder les adaptent aux interfaces de l'app (Flow, suspend), comme LiteRtLmEngine sur Android.
 */

/** Résultat d'un appel asynchrone du moteur Swift. */
interface NativeResult {
    fun onSuccess(value: String)
    fun onError(message: String)
}

/** Flux d'une réponse du modèle. */
interface NativeChatListener {
    fun onToken(text: String)
    fun onDone()
    fun onError(message: String)
}

/** Moteur du LLM, implémenté en Swift. */
interface NativeLlm {
    /** Charge le modèle (GPU Metal puis CPU) ; [callback] reçoit « GPU » ou « CPU ». */
    fun load(modelPath: String, preferGpu: Boolean, maxTokens: Int, cacheDir: String, callback: NativeResult)
    fun isLoaded(): Boolean

    /**
     * Nouvelle conversation (sans outils : leurs appels sont pilotés par Kotlin, voir [PromptToolLoop]).
     * [initialJson] : tableau `[{"role":"user"|"model","text":…}]` ; [maxOutputTokens] ≤ 0 = défaut du moteur.
     */
    fun newConversation(systemPrompt: String, initialJson: String, maxOutputTokens: Int)

    fun hasConversation(): Boolean
    fun send(message: String, listener: NativeChatListener)

    /** Tokens occupés dans la conversation (-1 si inconnu). */
    fun tokenCount(): Int
    fun cancel()

    /** Génération ponctuelle ; [jsonSchema] vide = texte libre. */
    fun generateOnce(systemPrompt: String, prompt: String, jsonSchema: String, maxOutputTokens: Int, callback: NativeResult)
    fun close()
}

/** Vecteurs d'embeddings rendus par le moteur Swift. */
interface NativeVectorsResult {
    fun onSuccess(vectors: List<List<Float>>)
    fun onError(message: String)
}

/** Modèle d'embeddings (EmbeddingGemma), implémenté en Swift. */
interface NativeEmbedder {
    fun load(modelPath: String, cacheDir: String, callback: NativeResult)
    fun isLoaded(): Boolean

    /** Vecteurs normalisés de dimension [dim] pour chaque texte. */
    fun embed(texts: List<String>, dim: Int, callback: NativeVectorsResult)
    fun release()
}

/** [LlmEngine] au-dessus du moteur Swift. */
class IosLlmEngine(private val native: NativeLlm, private val cacheDir: String) : LlmEngine {
    @Volatile private var loadedMaxTokens: Int? = null

    override fun isLoaded() = native.isLoaded()

    override suspend fun load(modelPath: String, preferGpu: Boolean, maxTokens: Int?): String {
        close()
        var lastError: Throwable? = null
        for (n in listOfNotNull(maxTokens, DEFAULT_MAX_TOKENS).distinct()) {
            try {
                val backend = awaitResult { native.load(modelPath, preferGpu, n, cacheDir, it) }
                loadedMaxTokens = n
                Log.d(TAG, "Modèle chargé en $backend avec maxNumTokens=$n")
                return backend
            } catch (t: Throwable) {
                Log.w(TAG, "Échec du chargement avec maxNumTokens=$n", t)
                lastError = t
            }
        }
        throw IllegalStateException("Impossible de charger le modèle : ${lastError?.message}", lastError)
    }

    override fun maxTokens(): Int = loadedMaxTokens ?: DEFAULT_MAX_TOKENS

    /** Outils de la conversation en cours (appelés par [PromptToolLoop]). */
    @Volatile private var tools: Map<String, AgentTool> = emptyMap()

    override fun newConversation(systemPrompt: String, tools: List<AgentTool>, initial: List<Msg>, prefill: Boolean, maxOutputTokens: Int?) {
        val initialJson = buildJsonArray {
            initial.forEach { m ->
                add(buildJsonObject {
                    put("role", if (m.role == MsgRole.USER) "user" else "model")
                    put("text", m.text)
                })
            }
        }.toString()
        this.tools = tools.associateBy { it.name }
        // L'API Swift de LiteRT-LM n'accepte que des outils déclarés en Swift : on décrit ceux de l'app dans le prompt.
        native.newConversation(PromptToolLoop.systemPrompt(systemPrompt, tools), initialJson, maxOutputTokens ?: 0)
    }

    /** Un tour brut du moteur Swift : les morceaux de texte, puis la fin. */
    private fun rawTurn(message: String): Flow<String> = callbackFlow {
        native.send(message, object : NativeChatListener {
            override fun onToken(text: String) { trySend(text) }
            override fun onDone() { close() }
            override fun onError(message: String) { close(IllegalStateException(message)) }
        })
        awaitClose { }
    }

    override fun send(message: String): Flow<ChatEvent> = PromptToolLoop.run(message, { tools }, ::rawTurn)

    override fun hasConversation() = native.hasConversation()

    override fun tokenCount(): Int? = native.tokenCount().takeIf { it >= 0 }

    override fun cancel() = native.cancel()

    override suspend fun generateOnce(systemPrompt: String, prompt: String, jsonSchema: String?, maxOutputTokens: Int?): String =
        awaitResult { native.generateOnce(systemPrompt, prompt, jsonSchema.orEmpty(), maxOutputTokens ?: 0, it) }

    override fun close() {
        native.close()
        loadedMaxTokens = null
    }

    private companion object {
        const val TAG = "IosLlmEngine"
        const val DEFAULT_MAX_TOKENS = 4096
    }
}

private suspend fun awaitResult(block: (NativeResult) -> Unit): String = suspendCancellableCoroutine { cont ->
    block(object : NativeResult {
        override fun onSuccess(value: String) { if (cont.isActive) cont.resume(value) }
        override fun onError(message: String) { if (cont.isActive) cont.resumeWithException(IllegalStateException(message)) }
    })
}

/** [AppTextEmbedder] au-dessus du moteur Swift (même découpage par lots et même quantification qu'Android). */
class IosTextEmbedder(private val native: NativeEmbedder, private val modelPath: String, private val cacheDir: String) : AppTextEmbedder {
    private val mutex = Mutex()
    @Volatile private var loadFailed = false
    @Volatile private var generation = 0

    override val loaded: Boolean get() = native.isLoaded()

    override suspend fun available(): Boolean = LocalFile(modelPath).let { it.isFile && it.length() > 0L }

    private suspend fun ensureLoaded(): Boolean {
        if (native.isLoaded()) return true
        if (loadFailed || !available()) return false
        return runCatching { awaitResult { native.load(modelPath, cacheDir, it) } }
            .onFailure { Log.w(TAG, "Échec du chargement du modèle d'embeddings", it); loadFailed = true }
            .isSuccess
    }

    private suspend fun embed(texts: List<String>): List<FloatArray>? = suspendCancellableCoroutine { cont ->
        native.embed(texts, Int8Vectors.DIM, object : NativeVectorsResult {
            override fun onSuccess(vectors: List<List<Float>>) { if (cont.isActive) cont.resume(vectors.map { it.toFloatArray() }) }
            override fun onError(message: String) { Log.w(TAG, message); if (cont.isActive) cont.resume(null) }
        })
    }

    override suspend fun embedQuery(text: String): ByteArray? = mutex.withLock {
        if (!ensureLoaded()) return@withLock null
        val v = embed(listOf(EmbeddingText.query(text)))?.singleOrNull() ?: return@withLock null
        if (v.size != Int8Vectors.DIM) null else Int8Vectors.quantize(v)
    }

    override suspend fun embedPassages(texts: List<String>): List<ByteArray>? {
        if (texts.isEmpty()) return if (available()) emptyList() else null
        val gen = generation
        val out = ArrayList<ByteArray>(texts.size)
        for (chunk in texts.chunked(BATCH)) {
            val vectors = mutex.withLock {
                if (generation != gen || !ensureLoaded()) return@withLock null
                embed(chunk)?.takeIf { it.size == chunk.size && it.all { v -> v.size == Int8Vectors.DIM } }
            } ?: return null
            vectors.forEach { out += Int8Vectors.quantize(it) }
        }
        return out
    }

    override fun release() {
        generation++
        loadFailed = false
        native.release()
    }

    private companion object {
        const val TAG = "IosTextEmbedder"
        const val BATCH = 16
    }
}
