package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class RavenHomeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidgetView(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        private const val PREFS_NAME = "raven_home_widget_prefs"
        private const val KEY_UNREAD_DM = "unread_dm"
        private const val KEY_STORIES_COUNT = "stories_count"
        private const val KEY_VOICE_LABEL = "voice_label"
        private const val KEY_LAST_TARGET_LABEL = "last_target_label"
        private const val KEY_OFFLINE_QUEUED = "offline_queued"

        const val EXTRA_WIDGET_ROUTE = "raven_widget_route"
        const val ROUTE_DM = "dm"
        const val ROUTE_VOICE = "voice"
        const val ROUTE_LAST_CHANNEL = "last_channel"

        fun saveSnapshotAndRefresh(
            context: Context,
            unreadDmCount: Int,
            activeStoriesCount: Int,
            activeVoiceLabel: String,
            lastChannelLabel: String,
            offlineQueuedCount: Int
        ) {
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_UNREAD_DM, unreadDmCount)
                    .putInt(KEY_STORIES_COUNT, activeStoriesCount)
                    .putString(KEY_VOICE_LABEL, activeVoiceLabel)
                    .putString(KEY_LAST_TARGET_LABEL, lastChannelLabel)
                    .putInt(KEY_OFFLINE_QUEUED, offlineQueuedCount)
                    .apply()

                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, RavenHomeWidgetProvider::class.java))
                ids.forEach { id -> updateWidgetView(context, mgr, id) }
            } catch (_: Exception) {
            }
        }

        private fun updateWidgetView(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val unreadDm = prefs.getInt(KEY_UNREAD_DM, 0)
                val storiesCount = prefs.getInt(KEY_STORIES_COUNT, 0)
                val voiceLabel = prefs.getString(KEY_VOICE_LABEL, null) ?: "Aucun salon vocal actif"
                val lastTarget = prefs.getString(KEY_LAST_TARGET_LABEL, null) ?: "#général"
                val queuedCount = prefs.getInt(KEY_OFFLINE_QUEUED, 0)

                val views = RemoteViews(context.packageName, R.layout.raven_home_widget)
                views.setTextViewText(
                    R.id.widget_unread_dm_text,
                    if (unreadDm > 0) "$unreadDm MP non lu(s)" else "MP à jour"
                )
                views.setTextViewText(
                    R.id.widget_stories_text,
                    "$storiesCount story(s) actives"
                )
                views.setTextViewText(
                    R.id.widget_voice_text,
                    voiceLabel + if (queuedCount > 0) " • $queuedCount en attente" else ""
                )
                views.setTextViewText(
                    R.id.widget_quick_open_btn,
                    "Ouvrir $lastTarget"
                )

                val openDmIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_WIDGET_ROUTE, ROUTE_DM)
                }
                val openChannelIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_WIDGET_ROUTE, ROUTE_LAST_CHANNEL)
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                views.setOnClickPendingIntent(
                    R.id.widget_dm_container,
                    PendingIntent.getActivity(context, 101, openDmIntent, flags)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_quick_open_btn,
                    PendingIntent.getActivity(context, 102, openChannelIntent, flags)
                )

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (_: Exception) {
            }
        }
    }
}
