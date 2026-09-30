package CodeSyS.Random_Number

import CodeSyS.Random_Number.data.AppSettings
import CodeSyS.Random_Number.ui.AppNav
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = (application as App).container.settingsRepository

        setContent {
            // Настройки темы/акцента из DataStore применяются на лету.
            val settings by settingsRepository.settings
                .collectAsState(initial = AppSettings.DEFAULT)

            RandomNumbersTheme(
                themeMode = settings.themeMode,
                accent = settings.accent,
            ) {
                AppNav()
            }
        }
    }
}
