package zhb.company.smart_home.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.util.Locale
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zhb.company.smart_home.MainActivity
import zhb.company.smart_home.R

/**
 * Home screen widget: outdoor, ground floor and upper floor temperature and whether the heat pump
 * runs. Small sizes show the temperatures only. A tap opens the app.
 */
class HomeWidget : GlanceAppWidget(), KoinComponent {

    private val store: WidgetStore by inject()

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = store.read()
        provideContent { GlanceTheme { Content(snapshot) } }
    }

    companion object {
        private val SMALL = DpSize(120.dp, 60.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
    }
}

@Composable
private fun Content(s: WidgetSnapshot?) {
    val context = LocalContext.current
    val wide = LocalSize.current.height >= 100.dp
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s == null) {
            Text(context.getString(R.string.widget_empty), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            return@Column
        }
        if (wide) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(heatingText(s.heating)),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(age(context, s.atMs), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
        }
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Reading(context.getString(R.string.widget_outdoor), s.outdoor, GlanceModifier.defaultWeight(), wide)
            Reading(context.getString(R.string.home_room_ground), s.ground, GlanceModifier.defaultWeight(), wide)
            Reading(context.getString(R.string.home_room_upper), s.upper, GlanceModifier.defaultWeight(), wide)
        }
    }
}

@Composable
private fun Reading(label: String, value: Float?, modifier: GlanceModifier, wide: Boolean) {
    Column(modifier = modifier) {
        Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
        Text(
            value?.let { String.format(Locale.ROOT, "%.1f°", it) } ?: "--",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (wide) 22.sp else 17.sp, fontWeight = FontWeight.Medium),
        )
    }
}

private fun heatingText(h: WidgetHeating): Int = when (h) {
    WidgetHeating.RUNNING -> R.string.widget_pump_running
    WidgetHeating.IDLE -> R.string.widget_pump_idle
    WidgetHeating.OFF -> R.string.widget_heating_off
    WidgetHeating.UNREACHABLE -> R.string.widget_heating_unreachable
    WidgetHeating.UNKNOWN -> R.string.widget_heating_unknown
}

/** Clock time of the values (a relative "5 min ago" would go stale between redraws). */
private fun age(context: Context, atMs: Long): String =
    context.getString(R.string.widget_at, java.time.Instant.ofEpochMilli(atMs).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))

class HomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
        WidgetRefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.cancel(context)
    }
}
