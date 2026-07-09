package dev.mtib.squadventure.phone

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.mtib.squadventure.R
import dev.mtib.squadventure.core.activity.ActivityRepository
import dev.mtib.squadventure.core.activity.GlobalStats
import dev.mtib.squadventure.phone.ui.Format

/**
 * Home-screen widget: total Squares / Mini-Squares / km, each with a "+N this week" delta. Reads
 * persisted totals from [ActivityRepository] (not the live singleton) so it shows numbers when the
 * app is idle. Pushed via [update] after saves and from the tracking service loop.
 */
class StatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = buildViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StatsWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val stats: GlobalStats = ActivityRepository(context).globalStats(System.currentTimeMillis())
            val week = context.getString(R.string.widget_this_week)
            return RemoteViews(context.packageName, R.layout.widget_stats).apply {
                setTextViewText(R.id.squares_value, stats.squadrats.toString())
                setTextViewText(R.id.squares_delta, delta(stats.weekSquadrats, week))
                setTextViewText(R.id.mini_value, stats.squadratinhos.toString())
                setTextViewText(R.id.mini_delta, delta(stats.weekSquadratinhos, week))
                setTextViewText(R.id.km_value, Format.km(stats.totalMeters))
                setTextViewText(R.id.km_delta, deltaKm(stats.weekMeters, week))
                setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
            }
        }

        private fun delta(n: Int, week: String): String = "+$n $week"

        private fun deltaKm(meters: Double, week: String): String = "+${Format.km(meters)} $week"

        private fun launchIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
