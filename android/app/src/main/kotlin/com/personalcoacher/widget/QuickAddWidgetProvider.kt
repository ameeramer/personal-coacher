package com.personalcoacher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.personalcoacher.R

/**
 * Widget provider for quick-add functionality.
 * Displays three buttons to quickly add Notes, Goals, or Tasks.
 */
class QuickAddWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Called when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Called when the last widget is removed
    }

    companion object {
        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)

            // Set up click handlers for each button
            views.setOnClickPendingIntent(
                R.id.btn_note,
                createPendingIntent(context, QuickAddType.NOTE, appWidgetId)
            )
            views.setOnClickPendingIntent(
                R.id.btn_goal,
                createPendingIntent(context, QuickAddType.GOAL, appWidgetId)
            )
            views.setOnClickPendingIntent(
                R.id.btn_task,
                createPendingIntent(context, QuickAddType.TASK, appWidgetId)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createPendingIntent(
            context: Context,
            type: QuickAddType,
            appWidgetId: Int
        ): PendingIntent {
            val intent = Intent(context, QuickAddActivity::class.java).apply {
                action = QuickAddActivity.ACTION_QUICK_ADD
                putExtra(QuickAddActivity.EXTRA_TYPE, type.name)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Add unique data to prevent intent reuse
                data = android.net.Uri.parse("widget://$appWidgetId/${type.name}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            return PendingIntent.getActivity(
                context,
                appWidgetId * 10 + type.ordinal,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

enum class QuickAddType {
    NOTE, GOAL, TASK
}
