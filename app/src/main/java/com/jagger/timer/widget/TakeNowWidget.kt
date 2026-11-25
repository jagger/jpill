package com.jagger.timer.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jagger.timer.MainActivity
import com.jagger.timer.core.data.local.entity.doseAmount
import dagger.hilt.android.EntryPointAccessors

/**
 * Home-screen widget for as-needed medicines: one thin ring per medicine that fills as the wait ends, its ready
 * time, and a Take now button. A ready medicine is logged in one tap; otherwise the tap opens the app's
 * confirmation, the same as the Take now button on Home.
 */
class TakeNowWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val rows = WidgetData.load(entry.pillRepository(), System.currentTimeMillis()).take(MAX_ROWS)
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val track = if (night) 0xFF26303B.toInt() else 0xFFD3DBE4.toInt()
        val rings = rows.associate { it.pillId to RingRenderer.render(it.progress, it.color, track) }
        provideContent { Content(context, rows, rings) }
    }

    @Composable
    private fun Content(context: Context, rows: List<WidgetRow>, rings: Map<Long, android.graphics.Bitmap>) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(Surface).cornerRadius(28.dp).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rows.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("No as-needed medicines yet. Tap to add one.", style = TextStyle(color = Muted, fontSize = 14.sp))
                }
            } else {
                rows.forEachIndexed { i, row ->
                    if (i > 0) Spacer(GlanceModifier.size(10.dp))
                    RowItem(context, row, rings.getValue(row.pillId))
                }
            }
        }
    }

    @Composable
    private fun RowItem(context: Context, row: WidgetRow, ring: android.graphics.Bitmap) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = GlanceModifier.size(44.dp), contentAlignment = Alignment.Center) {
                Image(ImageProvider(ring), contentDescription = null, modifier = GlanceModifier.size(44.dp))
                if (row.ready) Text("✓", style = TextStyle(color = Strong, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(row.name, maxLines = 1, style = TextStyle(color = Strong, fontSize = 16.sp, fontWeight = FontWeight.Medium))
                Text(row.caption, maxLines = 1, style = TextStyle(color = Muted, fontSize = 13.sp))
            }
            Spacer(GlanceModifier.width(8.dp))
            val onClick = if (row.takeDirectly) {
                actionRunCallback<TakeNowAction>(actionParametersOf(TakeNowAction.PILL_ID to row.pillId))
            } else {
                actionStartActivity(
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_CONFIRM_PILL_ID, row.pillId)
                        data = Uri.parse("jpill://confirm/${row.pillId}") // unique per medicine, so taps don't share one PendingIntent
                    }
                )
            }
            Box(
                modifier = GlanceModifier
                    .background(if (row.takeDirectly) ButtonFill else ButtonTonal)
                    .cornerRadius(20.dp)
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
                    .clickable(onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Take now",
                    style = TextStyle(
                        color = if (row.takeDirectly) ButtonFillText else ButtonTonalText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }

    private companion object {
        const val MAX_ROWS = 3
        val Surface = ColorProvider(day = Color(0xFFEEF2F6), night = Color(0xFF10151B))
        val Strong = ColorProvider(day = Color(0xFF0F1720), night = Color(0xFFE8EEF4))
        val Muted = ColorProvider(day = Color(0xFF5B6876), night = Color(0xFF8E9BAA))
        val ButtonFill = ColorProvider(day = Color(0xFF0B3A5C), night = Color(0xFFBFE3FF))
        val ButtonFillText = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF0B2438))
        val ButtonTonal = ColorProvider(day = Color(0xFFDCE4EC), night = Color(0xFF1F2A35))
        val ButtonTonalText = ColorProvider(day = Color(0xFF0B3A5C), night = Color(0xFFBFE3FF))
    }
}

/** One-tap dose from the widget. Re-checks readiness first so a stale widget can't log a dose that isn't due. */
class TakeNowAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val pillId = parameters[PILL_ID] ?: return
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val repo = entry.pillRepository()
        val now = System.currentTimeMillis()
        val row = WidgetData.load(repo, now).firstOrNull { it.pillId == pillId }
        val pill = repo.getPillById(pillId)
        if (row != null && row.takeDirectly && pill != null) {
            repo.logDose(
                com.jagger.timer.core.data.local.entity.DoseLogEntity(
                    pillId = pillId,
                    scheduleId = null,
                    timestamp = now,
                    status = com.jagger.timer.core.data.local.entity.DoseStatus.TAKEN,
                    amount = pill.doseAmount()
                )
            )
            entry.scheduleManager().refreshAlarmsForPill(pill) // also redraws the widget
        } else {
            TakeNowWidget().update(context, glanceId)
        }
    }

    companion object {
        val PILL_ID = ActionParameters.Key<Long>("pill_id")
    }
}
