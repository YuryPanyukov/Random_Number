package CodeSyS.Random_Number.platform

import CodeSyS.Random_Number.data.SettingsRepository
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Обёртка над тактильным/звуковым откликом приложения.
 *
 * Вызывается из ViewModel при генерации; сама решает, включены ли
 * отклики пользователем (настройки).
 */
interface FeedbackProvider {

    /**
     * Вызывается при успешной генерации [count] чисел.
     *
     * Реализация сама решает, сколько сигналов подать: по умолчанию один
     * на весь батч, а если пользователь включил настройку «отклик на
     * каждое число» — по одному на число.
     */
    fun onGenerated(count: Int)

    /** Освобождает системные ресурсы (вызывается, когда провайдер больше не нужен). */
    fun release() = Unit
}

/**
 * Системная реализация [FeedbackProvider]: короткая вибрация и/или
 * короткий сигнал через [ToneGenerator].
 *
 * Флаги вибрации/звука кешируются из [settingsRepository]
 * (сбор в фоновом scope приложения — живёт столько же, сколько приложение).
 *
 * @param context контекст приложения (не Activity).
 * @param settingsRepository источник настроек откликов.
 */
class SystemFeedbackProvider(
    private val context: Context,
    settingsRepository: SettingsRepository,
) : FeedbackProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var vibrationEnabled: Boolean = true

    @Volatile
    private var soundEnabled: Boolean = false

    @Volatile
    private var feedbackPerNumber: Boolean = false

    /** Ленивая инициализация под блокировкой: `onGenerated` может прийти с любого потока. */
    private val toneGeneratorLock = Any()
    private var toneGenerator: ToneGenerator? = null

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                vibrationEnabled = settings.vibrationEnabled
                soundEnabled = settings.soundEnabled
                feedbackPerNumber = settings.feedbackPerNumber
            }
        }
    }

    /**
     * Сигналы одного батча нельзя подавать подряд без пауз: несколько
     * вибраций подряд сливаются в один неразличимый импульс, а
     * [ToneGenerator] перезапускает тон на том же потоке. Поэтому при
     * батче из нескольких чисел сигналы растягиваются в фоне.
     */
    override fun onGenerated(count: Int) {
        if (!vibrationEnabled && !soundEnabled) return

        val repeat = if (feedbackPerNumber) count.coerceAtLeast(1) else 1
        if (repeat == 1) {
            signal()
            return
        }
        scope.launch {
            repeat(repeat) { index ->
                if (index > 0) delay(SIGNAL_INTERVAL_MILLIS)
                signal()
            }
        }
    }

    override fun release() {
        synchronized(toneGeneratorLock) {
            toneGenerator?.release()
            toneGenerator = null
        }
        scope.coroutineContext.cancel()
    }

    /** Один сигнал: вибрация и/или короткий тон. */
    private fun signal() {
        if (vibrationEnabled) vibrate()
        if (soundEnabled) beep()
    }

    private fun vibrate() {
        runCatching {
            val vibrator = currentVibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(35)
            }
        }
    }

    private fun currentVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun beep() {
        runCatching {
            val generator = synchronized(toneGeneratorLock) {
                toneGenerator ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                    .also { toneGenerator = it }
            }
            generator.startTone(ToneGenerator.TONE_PROP_ACK, 90)
        }
    }

    private companion object {
        /** Пауза между сигналами одного батча, мс. */
        const val SIGNAL_INTERVAL_MILLIS = 110L
    }
}
