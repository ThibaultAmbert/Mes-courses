import Foundation
import LiteRTLM
import Shared

/// Moteur du LLM local (LiteRT-LM, GPU Metal puis CPU), branché sur l'interface Kotlin `NativeLlm`.
/// Les outils de l'app sont décrits dans le prompt et exécutés côté Kotlin (PromptToolLoop) : l'API Swift
/// n'accepte que des outils déclarés en Swift.
final class LiteRtLlm: NSObject, NativeLlm, @unchecked Sendable {
    private let lock = NSLock()
    private var engine: Engine?
    private var conversation: Conversation?
    /// Conversation demandée mais pas encore créée (création asynchrone au premier message).
    private var pending: (system: String, initial: [Message])?
    private var maxOutput: Int?
    private var task: Task<Void, Never>?

    private func locked<T>(_ body: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return body() }

    func load(modelPath: String, preferGpu: Bool, maxTokens: Int32, cacheDir: String, callback: NativeResult) {
        close()
        Task.detached { [self] in
            var lastError: Error?
            let backends: [(String, Backend)] = preferGpu ? [("GPU", .gpu), ("CPU", .cpu())] : [("CPU", .cpu())]
            for (label, backend) in backends {
                do {
                    let config = try EngineConfig(modelPath: modelPath, backend: backend, maxNumTokens: Int(maxTokens), cacheDir: cacheDir)
                    let e = Engine(engineConfig: config)
                    try await e.initialize()
                    locked { engine = e }
                    callback.onSuccess(value: label)
                    return
                } catch {
                    lastError = error
                }
            }
            callback.onError(message: "Chargement impossible : \(lastError.map { String(describing: $0) } ?? "erreur inconnue")")
        }
    }

    func isLoaded() -> Bool { locked { engine != nil } }

    func startConversation(systemPrompt: String, initialJson: String, maxOutputTokens: Int32) {
        var initial: [Message] = []
        if let data = initialJson.data(using: .utf8),
           let list = try? JSONSerialization.jsonObject(with: data) as? [[String: String]] {
            initial = list.compactMap { m in
                guard let text = m["text"] else { return nil }
                return Message(text, role: m["role"] == "user" ? .user : .model)
            }
        }
        locked {
            conversation = nil
            pending = (systemPrompt, initial)
            maxOutput = maxOutputTokens > 0 ? Int(maxOutputTokens) : nil
        }
    }

    func hasConversation() -> Bool { locked { conversation != nil || pending != nil } }

    private func currentConversation() async throws -> Conversation {
        let (existing, request, e) = locked { (conversation, pending, engine) }
        if let c = existing { return c }
        guard let e, let request else { throw NSError(domain: "MesCourses", code: 1, userInfo: [NSLocalizedDescriptionKey: "Aucune conversation"]) }
        let config = ConversationConfig(
            systemMessage: Message(request.system, role: .system),
            initialMessages: request.initial,
            samplerConfig: try SamplerConfig(topK: 40, topP: 0.95, temperature: 0.7),
            thinkingConfig: ThinkingConfig(enableThinking: false)
        )
        let c = try await e.createConversation(with: config)
        locked { conversation = c; pending = nil }
        return c
    }

    func send(message: String, listener: NativeChatListener) {
        let limit = locked { maxOutput }
        let t = Task.detached { [self] in
            do {
                let c = try await currentConversation()
                for try await chunk in c.sendMessageStream(Message(message), maxOutputTokens: limit) {
                    if Task.isCancelled { break }
                    listener.onToken(text: chunk.toString)
                }
                listener.onDone()
            } catch {
                listener.onError(message: String(describing: error))
            }
        }
        locked { task = t }
    }

    func usedTokens() -> Int32 {
        guard let c = locked({ conversation }), let n = try? c.getTokenCount() else { return -1 }
        return Int32(n)
    }

    func cancel() {
        let (c, t) = locked { (conversation, task) }
        try? c?.cancel()
        t?.cancel()
    }

    func generateOnce(systemPrompt: String, prompt: String, jsonSchema: String, maxOutputTokens: Int32, callback: NativeResult) {
        Task.detached { [self] in
            do {
                guard let e = locked({ engine }) else { throw NSError(domain: "MesCourses", code: 2, userInfo: [NSLocalizedDescriptionKey: "Modèle non chargé"]) }
                // Comme sur Android : la conversation en cours est fermée (elle sera rouverte avec un résumé).
                locked { conversation = nil; pending = nil }
                let config = ConversationConfig(
                    systemMessage: Message(systemPrompt, role: .system),
                    samplerConfig: try SamplerConfig(topK: 20, topP: 0.9, temperature: 0.4),
                    thinkingConfig: ThinkingConfig(enableThinking: false),
                    enableResponseFormat: !jsonSchema.isEmpty
                )
                let temp = try await e.createConversation(with: config)
                let format = jsonSchema.isEmpty ? nil : try ResponseFormat.json(schema: jsonSchema)
                let reply = try await temp.sendMessage(
                    Message(prompt),
                    maxOutputTokens: maxOutputTokens > 0 ? Int(maxOutputTokens) : nil,
                    responseFormat: format
                )
                callback.onSuccess(value: reply.toString)
            } catch {
                callback.onError(message: String(describing: error))
            }
        }
    }

    /// LiteRT-LM libère le moteur et la conversation quand on lâche les références.
    func close() {
        let t = locked { () -> Task<Void, Never>? in
            let current = task
            conversation = nil
            pending = nil
            engine = nil
            task = nil
            return current
        }
        t?.cancel()
    }
}

/// Modèle d'embeddings (EmbeddingGemma, CPU), branché sur l'interface Kotlin `NativeEmbedder`.
final class LiteRtEmbedder: NSObject, NativeEmbedder, @unchecked Sendable {
    private let lock = NSLock()
    private var engine: EmbeddingEngine?

    private func locked<T>(_ body: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return body() }

    func load(modelPath: String, cacheDir: String, callback: NativeResult) {
        Task.detached { [self] in
            do {
                let e = EmbeddingEngine(config: EmbeddingEngineConfig(modelPath: modelPath, backend: .cpu(), cacheDir: cacheDir))
                try await e.initialize()
                locked { engine = e }
                callback.onSuccess(value: "CPU")
            } catch {
                callback.onError(message: String(describing: error))
            }
        }
    }

    func isLoaded() -> Bool { locked { engine != nil } }

    func embed(texts: [String], dim: Int32, callback: NativeVectorsResult) {
        Task.detached { [self] in
            do {
                guard let e = locked({ engine }) else { throw NSError(domain: "MesCourses", code: 3, userInfo: [NSLocalizedDescriptionKey: "Modèle d'embeddings non chargé"]) }
                let options = EmbeddingOptions(normalize: true, outputSize: Int(dim))
                let responses = try await e.computeEmbeddingBatch(contentsBatch: texts.map { [Content.text($0)] }, options: options)
                callback.onSuccess(vectors: responses.map { r in r.embedding.map { KotlinFloat(value: $0) } })
            } catch {
                callback.onError(message: String(describing: error))
            }
        }
    }

    func unload() {
        let e = locked { () -> EmbeddingEngine? in let current = engine; engine = nil; return current }
        if let e { Task { await e.close() } }
    }
}
