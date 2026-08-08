package com.lifetrace.finance.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lifetrace.finance.MainActivity
import com.lifetrace.finance.R

class FinanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_finance)
            views.setOnClickPendingIntent(R.id.widget_expense, quickIntent(context, "quick", 101))
            views.setOnClickPendingIntent(R.id.widget_income, quickIntent(context, "quick", 102, "income"))
            views.setOnClickPendingIntent(R.id.widget_inbox, quickIntent(context, "inbox", 103))
            manager.updateAppWidget(id, views)
        }
    }

    private fun quickIntent(context: Context, destination: String, requestCode: Int, type: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("destination", destination)
            .putExtra("transaction_type", type)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
