package com.example.data

import com.example.model.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

object PluginFirestoreSync {

    const val SUBCOLLECTION_PLUGINS = "plugins"
    const val SUBCOLLECTION_PLUGIN_LOGS = "plugin_logs"

    fun stateToMap(state: UserPluginState): Map<String, Any?> = mapOf(
        "pluginId" to state.pluginId,
        "installedVersion" to state.installedVersion,
        "isEnabled" to state.isEnabled,
        "lifecycleState" to state.lifecycleState.name,
        "grantedPermissions" to state.grantedPermissions.map { it.name },
        "deniedPermissions" to state.deniedPermissions.map { it.name },
        "configValues" to state.configValues,
        "consecutiveErrors" to state.consecutiveErrors,
        "lastErrorMessage" to state.lastErrorMessage,
        "updatedAtMs" to state.updatedAtMs,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun mapDocToState(doc: DocumentSnapshot): UserPluginState? {
        if (!doc.exists()) return null
        val pluginId = doc.getString("pluginId") ?: doc.id
        val version = doc.getString("installedVersion") ?: return null
        val lifecycleRaw = doc.getString("lifecycleState") ?: PluginLifecycleState.DISABLED.name
        val lifecycle = enumValues<PluginLifecycleState>().firstOrNull { it.name == lifecycleRaw }
            ?: PluginLifecycleState.DISABLED
        val granted = (doc.get("grantedPermissions") as? List<*>).orEmpty()
            .mapNotNull { raw -> enumValues<PluginPermission>().firstOrNull { it.name == raw.toString() } }
            .toSet()
        val denied = (doc.get("deniedPermissions") as? List<*>).orEmpty()
            .mapNotNull { raw -> enumValues<PluginPermission>().firstOrNull { it.name == raw.toString() } }
            .toSet()
        val rawCfg = (doc.get("configValues") as? Map<*, *>).orEmpty()
        val cfg = rawCfg.entries.mapNotNull { (k, v) ->
            val ks = k?.toString() ?: return@mapNotNull null
            val vs = v?.toString() ?: return@mapNotNull null
            ks to vs
        }.toMap()

        return UserPluginState(
            pluginId = pluginId,
            installedVersion = version,
            isEnabled = doc.getBoolean("isEnabled") ?: false,
            lifecycleState = lifecycle,
            grantedPermissions = granted,
            deniedPermissions = denied,
            configValues = cfg,
            consecutiveErrors = (doc.getLong("consecutiveErrors") ?: 0L).toInt(),
            lastErrorMessage = doc.getString("lastErrorMessage"),
            updatedAtMs = doc.getLong("updatedAtMs") ?: System.currentTimeMillis()
        )
    }

    fun logToMap(entry: PluginLogEntry): Map<String, Any?> = mapOf(
        "id" to entry.id,
        "pluginId" to entry.pluginId,
        "pluginName" to entry.pluginName,
        "level" to entry.level.name,
        "action" to entry.action,
        "message" to entry.message,
        "timestampMs" to entry.timestampMs,
        "timestampLabel" to entry.timestampLabel,
        "createdAt" to FieldValue.serverTimestamp()
    )

    fun mapDocToLog(doc: DocumentSnapshot): PluginLogEntry? {
        if (!doc.exists()) return null
        val pluginId = doc.getString("pluginId") ?: return null
        val levelRaw = doc.getString("level") ?: PluginLogLevel.INFO.name
        val level = enumValues<PluginLogLevel>().firstOrNull { it.name == levelRaw } ?: PluginLogLevel.INFO
        return PluginLogEntry(
            id = doc.getString("id") ?: doc.id,
            pluginId = pluginId,
            pluginName = doc.getString("pluginName") ?: pluginId,
            level = level,
            action = doc.getString("action") ?: "EVENT",
            message = doc.getString("message").orEmpty(),
            timestampMs = doc.getLong("timestampMs") ?: System.currentTimeMillis(),
            timestampLabel = doc.getString("timestampLabel").orEmpty()
        )
    }
}
