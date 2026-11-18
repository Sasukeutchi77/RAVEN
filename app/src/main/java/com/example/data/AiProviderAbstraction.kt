package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

enum class AiStructuredSchemaSpec {
    MODERATION_REPORT,
    ANNOUNCEMENT_DRAFT,
    EVENT_DRAFT
}

data class AiProviderRequest(
    val systemInstruction: String,
    val conversationHistory: List<Pair<String, String>> = emptyList(),
    val prompt: String,
    val modelName: String = DEFAULT_MODEL_NAME,
    val temperature: Float = 0.6f,
    val maxOutputTokens: Int = 1024
) {
    companion object {
        const val DEFAULT_MODEL_NAME = "gemini-3.8-flash"
    }
}

enum class AiErrorKind {
    TIMEOUT,
    UNAUTHENTICATED_OR_APP_CHECK,
    RATE_LIMIT_OR_QUOTA,
    NETWORK,
    SAFETY_OR_INVALID,
    EMPTY_RESPONSE,
    UNKNOWN
}

class AiProviderException(
    val kind: AiErrorKind,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Abstract AI provider contract allowing RAVEN AI to route through Firebase AI Logic (Gemini + App Check)
 * today or any future secure backend proxy without changing repositories, ViewModels, or UI.
 */
interface RavenAiProvider {
    val providerId: String
    val modelDisplayName: String
    suspend fun generateText(request: AiProviderRequest): String
    fun generateTextStream(request: AiProviderRequest): Flow<String>
    suspend fun generateStructuredJson(
        request: AiProviderRequest,
        schemaSpec: AiStructuredSchemaSpec
    ): String
}

/**
 * Production AI provider backed by Firebase AI Logic (`Firebase.ai`) and App Check attestation.
 * Contains zero client-side API keys.
 */
class FirebaseAiLogicProvider(
    private val defaultModel: String = AiProviderRequest.DEFAULT_MODEL_NAME,
    private val timeoutMs: Long = 60_000L
) : RavenAiProvider {

    companion object {
        private const val TAG = "FirebaseAiLogic"
    }

    override val providerId: String = "firebase_ai_logic"
    override val modelDisplayName: String = "Firebase AI Logic • $defaultModel"

    private val defaultTextModel by lazy {
        Firebase.ai.generativeModel(modelName = defaultModel)
    }

    private val moderationModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "isSpam" to Schema.boolean("Indique si le contenu correspond à du spam"),
                "isSuspicious" to Schema.boolean("Indique si le contenu semble suspect (phishing, arnaque, usurpation)"),
                "riskScore" to Schema.integer("Score de risque entre 0 et 100"),
                "riskLevel" to Schema.enumeration(listOf("LOW", "MEDIUM", "HIGH", "CRITICAL"), "Niveau de risque"),
                "categories" to Schema.array(Schema.string(), "Catégories de risque détectées"),
                "signals" to Schema.array(Schema.string(), "Indices concrets observés"),
                "recommendedAction" to Schema.string("Action recommandée pour la modération"),
                "explanation" to Schema.string("Explication concise en français")
            )
        )
        Firebase.ai.generativeModel(
            modelName = defaultModel,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val announcementModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string("Titre clair de l'annonce communautaire"),
                "content" to Schema.string("Contenu structuré et prêt à publier en français"),
                "suggestedPinned" to Schema.boolean("S'il est recommandé d'épingler cette annonce"),
                "toneSummary" to Schema.string("Ton adopté (ex: Professionnel, Enthousiaste, Urgent)")
            )
        )
        Firebase.ai.generativeModel(
            modelName = defaultModel,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val eventModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string("Titre accrocheur de l'événement"),
                "description" to Schema.string("Description claire avec déroulement"),
                "suggestedDateTimeLabel" to Schema.string("Créneau suggéré (ex: Vendredi 20h30)"),
                "suggestedLocationLabel" to Schema.string("Lieu suggéré (ex: Salon Vocal Principal)"),
                "checklist" to Schema.array(Schema.string(), "Étapes de préparation pour les organisateurs")
            )
        )
        Firebase.ai.generativeModel(
            modelName = defaultModel,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private fun buildModelForRequest(request: AiProviderRequest) =
        if (request.systemInstruction.isBlank() && request.modelName == defaultModel) {
            defaultTextModel
        } else {
            Firebase.ai.generativeModel(
                modelName = request.modelName.ifBlank { defaultModel },
                systemInstruction = content { text(request.systemInstruction) }
            )
        }

    private fun buildPromptContent(request: AiProviderRequest) = content("user") {
        if (request.conversationHistory.isNotEmpty()) {
            val hist = request.conversationHistory.takeLast(8).joinToString("\n") { (role, txt) ->
                "[$role]: ${txt.take(600)}"
            }
            text("Historique récent:\n$hist\n\nDemande actuelle:\n${request.prompt}")
        } else {
            text(request.prompt)
        }
    }

    override suspend fun generateText(request: AiProviderRequest): String {
        return try {
            val model = buildModelForRequest(request)
            val response = withTimeout(timeoutMs) {
                model.generateContent(buildPromptContent(request))
            }
            val text = response.text?.trim()
            if (text.isNullOrBlank()) {
                throw AiProviderException(
                    AiErrorKind.EMPTY_RESPONSE,
                    "L'assistant IA a renvoyé une réponse vide."
                )
            }
            text
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI unary generation timed out", e)
            throw AiProviderException(
                AiErrorKind.TIMEOUT,
                "Délai d'attente dépassé (60s). Veuillez réessayer.",
                e
            )
        } catch (e: AiProviderException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            throw mapException(e)
        }
    }

    override fun generateTextStream(request: AiProviderRequest): Flow<String> = flow {
        val model = buildModelForRequest(request)
        var emittedAny = false
        withTimeout(timeoutMs) {
            model.generateContentStream(buildPromptContent(request)).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    emittedAny = true
                    emit(chunkText)
                }
            }
        }
        if (!emittedAny) {
            throw AiProviderException(
                AiErrorKind.EMPTY_RESPONSE,
                "L'assistant IA n'a produit aucun contenu."
            )
        }
    }.catch { error ->
        if (error is TimeoutCancellationException) {
            Log.e(TAG, "AI streaming timed out", error)
            throw AiProviderException(
                AiErrorKind.TIMEOUT,
                "Le streaming IA a expiré après 60 secondes.",
                error
            )
        }
        if (error is CancellationException) throw error
        if (error is AiProviderException) throw error
        Log.e(TAG, "Firebase AI streaming failed", error)
        throw mapException(error)
    }

    override suspend fun generateStructuredJson(
        request: AiProviderRequest,
        schemaSpec: AiStructuredSchemaSpec
    ): String {
        return try {
            val model = when (schemaSpec) {
                AiStructuredSchemaSpec.MODERATION_REPORT -> moderationModel
                AiStructuredSchemaSpec.ANNOUNCEMENT_DRAFT -> announcementModel
                AiStructuredSchemaSpec.EVENT_DRAFT -> eventModel
            }
            val promptWithSystem = buildString {
                if (request.systemInstruction.isNotBlank()) {
                    append(request.systemInstruction).append("\n\n")
                }
                append(request.prompt)
            }
            val response = withTimeout(timeoutMs) {
                model.generateContent(promptWithSystem)
            }
            val json = response.text?.trim()
            if (json.isNullOrBlank()) {
                throw AiProviderException(
                    AiErrorKind.EMPTY_RESPONSE,
                    "Réponse JSON structurée vide de l'assistant IA."
                )
            }
            json
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI structured JSON generation timed out", e)
            throw AiProviderException(
                AiErrorKind.TIMEOUT,
                "Délai d'attente dépassé lors de l'analyse structurée.",
                e
            )
        } catch (e: AiProviderException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI structured call failed", e)
            throw mapException(e)
        }
    }

    private fun mapException(error: Throwable): AiProviderException {
        val raw = error.message.orEmpty()
        val lower = raw.lowercase()
        val kind = when {
            lower.contains("403") || lower.contains("app check") || lower.contains("appcheck") ||
                lower.contains("unauthenticated") || lower.contains("permission") ->
                AiErrorKind.UNAUTHENTICATED_OR_APP_CHECK
            lower.contains("429") || lower.contains("quota") || lower.contains("resource_exhausted") ->
                AiErrorKind.RATE_LIMIT_OR_QUOTA
            lower.contains("network") || lower.contains("unable to resolve") ||
                lower.contains("timeout") || lower.contains("unavailable") || lower.contains("connect") ->
                AiErrorKind.NETWORK
            lower.contains("safety") || lower.contains("blocked") ->
                AiErrorKind.SAFETY_OR_INVALID
            else -> AiErrorKind.UNKNOWN
        }
        val friendly = when (kind) {
            AiErrorKind.UNAUTHENTICATED_OR_APP_CHECK ->
                "Vérification App Check ou authentification requise pour contacter RAVEN AI."
            AiErrorKind.RATE_LIMIT_OR_QUOTA ->
                "Limite de requêtes du service IA atteinte temporairement. Patientez un instant."
            AiErrorKind.NETWORK ->
                "Connexion réseau indisponible pour joindre RAVEN AI. Vérifiez votre accès Internet."
            AiErrorKind.SAFETY_OR_INVALID ->
                "Cette requête a été bloquée par les filtres de sécurité du modèle."
            else -> raw.ifBlank { "Erreur inattendue du service RAVEN AI." }
        }
        return AiProviderException(kind, friendly, error)
    }
}
