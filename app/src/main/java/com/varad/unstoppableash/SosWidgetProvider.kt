package com.varad.unstoppableash

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.varad.unstoppableash.feature.safety.SosHoldActivity

/**
 * The home-screen SOS widget (same class name as v1, so placed widgets keep working). A tap opens the
 * hold-to-send screen; nothing is ever sent by a single tap (D-3).
 */
class SosWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, SosHoldActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_sos).apply {
            setOnClickPendingIntent(R.id.widget_button_container, open)
        }
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }
}
