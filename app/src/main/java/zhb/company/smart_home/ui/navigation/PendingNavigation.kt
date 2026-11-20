package zhb.company.smart_home.ui.navigation

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A screen to open once the user is signed in and connected, e.g. from a tapped notification.
 * The nav graph consumes it as soon as it leaves the sign-in flow.
 */
class PendingNavigation {
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()

    /** Reads the notifier's `data` extras (FCM puts them on the launch intent). */
    fun handle(intent: Intent?) {
        val extras = intent?.extras ?: return
        if (extras.getString("type") == "report" && extras.getString("kind") == "heating") {
            val date = extras.getString("date") ?: return
            if (DATE.matches(date)) _route.value = Destination.ReportDetail.of(date)
        } else if (extras.getString("type") != null && extras.getString("title") != null) {
            // Any other notifier message (node, heating, system): the list, where it can be labeled.
            _route.value = Destination.Notifications.route
        }
    }

    fun consume(): String? = _route.value.also { _route.value = null }

    private companion object {
        val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
