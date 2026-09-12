package com.varad.unstoppableash

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.firebase.database.FirebaseDatabase

class SosWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_SOS) {
            triggerSos()
        }
    }

    private fun triggerSos() {
        val database = FirebaseDatabase.getInstance().reference
        val alertData = mapOf(
            "isSosActive" to true,
            "timestamp" to System.currentTimeMillis()
        )
        database.child("alerts").push().setValue(alertData)
    }

    companion object {
        const val ACTION_WIDGET_SOS = "com.varad.unstoppableash.ACTION_WIDGET_SOS"

        internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val intent = Intent(context, SosWidgetProvider::class.java)
            intent.action = ACTION_WIDGET_SOS
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_sos)
            views.setOnClickPendingIntent(R.id.widget_button_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
