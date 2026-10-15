package CodeSyS.Random_Number.widget

import CodeSyS.Random_Number.App
import CodeSyS.Random_Number.R
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

/**
 * Плитка быстрых настроек «Сгенерировать» (задача 5.4).
 *
 * Один тап делает шаг в последней сессии и обновляет виджет, не открывая
 * приложение. Логика генерации — общая с виджетом (`App.container` →
 * `quickGenerate`), поэтому поведение одинаково.
 */
class QuickGenerateTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val container = (application as App).container
        val appContext = applicationContext

        // Генерация и перерисовка виджета — вне главного потока: тап по
        // плитке не должен блокировать системный UI быстрых настроек.
        thread(name = "quick-generate-tile") {
            runBlocking {
                container.quickGenerate.generateLatest()
                LatestSessionWidget().refresh(appContext)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.tile_label)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
