package com.example.util

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

object FirestoreErrorHandler {
    fun handleFirestoreError(
        exception: Exception,
        operationType: OperationType,
        path: String?,
        uid: String? = null
    ): String {
        val codeName = (exception as? FirebaseFirestoreException)?.code?.name
            ?: (exception.cause as? FirebaseFirestoreException)?.code?.name
            ?: "UNKNOWN"

        val errJson = try {
            JSONObject().apply {
                put("error", exception.message ?: exception.javaClass.simpleName)
                put("code", codeName)
                put("operationType", operationType.name)
                put("path", path ?: "")
                put("uid", uid ?: "")
            }.toString()
        } catch (_: Exception) {
            "{\"error\":\"${exception.message}\",\"operationType\":\"${operationType.name}\",\"path\":\"$path\"}"
        }

        Log.e("FirestoreError", errJson, exception)

        return when (codeName) {
            "PERMISSION_DENIED" -> "Permission refusée par les règles serveur ($operationType)."
            "NOT_FOUND" -> "Ressource introuvable dans le cloud."
            "ALREADY_EXISTS" -> "Cet identifiant existe déjà."
            "UNAVAILABLE" -> "Service temporairement indisponible. Vérifiez votre connexion."
            else -> exception.message ?: "Erreur lors de l'opération Firestore ($operationType)."
        }
    }
}
