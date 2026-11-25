package zhb.company.smart_home.widget

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import zhb.company.smart_home.R
import zhb.company.smart_home.data.door.DoorOpener
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.repository.AuthRepository
import zhb.company.smart_home.di.APP_SCOPE

/**
 * Quick Settings tile that opens the front door, reachable from any app. It is an active tile
 * (`ACTIVE_TILE` in the manifest) that never asks to listen, so the system does not bind it when the
 * panel opens; it runs only for a tap. On a locked phone Android asks to unlock first.
 */
class DoorTileService : TileService(), KoinComponent {
    private val opener: DoorOpener by inject()
    private val auth: AuthRepository by inject()
    private val scope: CoroutineScope by inject(named(APP_SCOPE))

    override fun onClick() {
        if (isLocked) unlockAndRun { open() } else open()
    }

    private fun open() {
        show(Tile.STATE_ACTIVE, getString(R.string.door_tile_opening))
        val app = applicationContext
        scope.launch {
            val outcome = withTimeoutOrNull(BUDGET_MS) { opener.open() } ?: CommandOutcome.Timeout
            val message = when {
                outcome is CommandOutcome.NotConnected && !auth.hasSavedSession() -> R.string.door_widget_signed_out
                outcome is CommandOutcome.Accepted -> R.string.home_door_opened
                outcome is CommandOutcome.Rejected -> R.string.garden_msg_failed
                outcome is CommandOutcome.Timeout -> R.string.heating_msg_timeout
                outcome is CommandOutcome.NotConnected -> R.string.heating_msg_not_connected
                else -> null // a tap from the widget or the tile is still being handled
            }
            android.os.Handler(app.mainLooper).post {
                show(Tile.STATE_INACTIVE, null)
                if (message != null) Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Updates the tile while the system still has it bound; afterwards there is nothing to update. */
    private fun show(state: Int, subtitle: String?) {
        val tile = runCatching { qsTile }.getOrNull() ?: return
        tile.state = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        runCatching { tile.updateTile() }
    }

    private companion object {
        const val BUDGET_MS = 9_000L
    }
}
