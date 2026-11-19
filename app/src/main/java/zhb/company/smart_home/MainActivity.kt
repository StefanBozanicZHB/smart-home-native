package zhb.company.smart_home

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.ui.ThemeStateHolder
import zhb.company.smart_home.ui.navigation.SmartHomeNavGraph
import zhb.company.smart_home.ui.theme.SmartHomeTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeStateHolder = koinViewModel()
            val darkTheme by themeViewModel.isDarkTheme.collectAsState(initial = false)
            SmartHomeTheme(darkTheme = darkTheme) {
                SmartHomeNavGraph()
            }
        }
    }
}
