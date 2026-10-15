package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.data.DefaultSessionTexts
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionTexts
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Источник для seed честного розыгрыша (см. [CodeSyS.Random_Number.platform.HonestDraw]). */
private const val SECURE_SOURCE = "SecureRandom"

/**
 * Собирает текст для шаринга: заголовок сессии + все значения истории.
 *
 * Пустая история — только заголовок. Если у сессии задан seed, к тексту
 * добавляется seed и его источник (криптографический или ручной), а также
 * подсказка о воспроизводимости — так результат честного розыгрыша можно
 * проверить независимо.
 *
 * @param texts локализованные строки (заголовок, источник ручного seed,
 * подсказка о проверке); по умолчанию — [DefaultSessionTexts].
 */
fun buildShareText(session: Session, texts: SessionTexts = DefaultSessionTexts): String =
    buildString {
        append(texts.title(session))
        val values = session.generatedDisplay(texts)
        if (values.isNotEmpty()) {
            append(": ")
            append(values.joinToString(", "))
        }
        val seed = session.seed
        if (seed != null) {
            append("\nSeed: ")
            append(seed)
            append(" (")
            append(if (session.seedFromSecure) SECURE_SOURCE else texts.manualSeedSource)
            append(')')
            append("\n")
            append(texts.shareVerificationHint)
        }
    }

/**
 * Открывает системный share-sheet ([Intent.ACTION_SEND]) с текстом [text].
 */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * Предлагает сохранить [text] в файл через системный диалог
 * ([Intent.ACTION_CREATE_DOCUMENT]).
 *
 * @return `Intent`, который нужно запустить из Activity (метод
 * `startActivityForResult`), либо `null`, если сохранение недоступно.
 */
fun createSaveDocumentIntent(text: String, fileName: String, mimeType: String): Intent? =
    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = mimeType
        putExtra(Intent.EXTRA_TITLE, fileName)
        putExtra(Intent.EXTRA_TEXT, text)
    }

/**
 * Строит PNG-карточку результата: заголовок сессии и крупное итоговое
 * значение на градиентной плашке. Рисуется вручную на [android.graphics.Canvas],
 * поэтому не требует Compose-сцены и работает без Activity.
 *
 * @param title название сессии (первая строка карточки).
 * @param value итоговое значение — то, что показано крупным шрифтом.
 * @param width пиксели по горизонтали (высота подберётся по тексту).
 */
fun buildResultCardBitmap(title: String, value: String, width: Int = CARD_WIDTH): Bitmap {
    val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_VALUE_COLOR
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = width * 0.16f
    }
    // Крупный текст не вылезает за ширину: сжимаем при необходимости.
    valuePaint.textSize = fitValueTextSize(valuePaint, value, width)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_TITLE_COLOR
        textAlign = Paint.Align.CENTER
        textSize = width * 0.045f
    }

    // Перенос длинных значений (списки, перестановки).
    val textWidth = width - 2 * CARD_PADDING
    val valueLayout = StaticLayout.Builder
        .obtain(value, 0, value.length, valuePaint, textWidth)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .build()

    val height = CARD_TOP_PADDING + titlePaint.fontSpacing +
        valueLayout.height + CARD_BOTTOM_PADDING

    val bitmap = Bitmap.createBitmap(width, height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawPaint(
        Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height,
                CARD_BG_START, CARD_BG_END,
                Shader.TileMode.CLAMP,
            )
        },
    )

    val titleY = CARD_TOP_PADDING + titlePaint.fontSpacing
    canvas.drawText(title, width / 2f, titleY, titlePaint)
    canvas.save()
    canvas.translate(
        width / 2f - textWidth / 2f,
        titleY + CARD_TEXT_GAP,
    )
    valueLayout.draw(canvas)
    canvas.restore()
    return bitmap
}

/**
 * Делит ширину шрифта, пока строка результата (без переноса) не поместится.
 */
private fun fitValueTextSize(paint: TextPaint, value: String, width: Int): Float {
    val maxWidth = width - 2 * CARD_PADDING
    var size = paint.textSize
    while (size > width * MIN_VALUE_TEXT_FACTOR && paint.measureText(value) > maxWidth) {
        size *= 0.92f
        paint.textSize = size
    }
    return size
}

/**
 * Шаринг PNG-карточки: файл кладётся в кэш `share/` и отдаётся системному
 * share-sheet через [FileProvider]. Все приложения получатели получают
 * только право чтения копии.
 */
fun shareResultCardImage(context: Context, bitmap: Bitmap) {
    val dir = File(context.cacheDir, SHARE_DIR)
    if (!dir.exists()) dir.mkdirs()
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val file = File(dir, "result_$stamp.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + PROVIDER_SUFFIX,
        file,
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Ширина карточки по умолчанию (пиксели). */
private const val CARD_WIDTH = 1080

/** Внутренние отступы карточки (пиксели). */
private const val CARD_PADDING = 96
private const val CARD_TOP_PADDING = 120f
private const val CARD_BOTTOM_PADDING = 140f
private const val CARD_TEXT_GAP = 32f

/** Нижняя граница размера шрифта результата (множитель от ширины). */
private const val MIN_VALUE_TEXT_FACTOR = 0.04f

/** Палитра карточки: тёмный градиент, светлое значение, серый заголовок. */
private const val CARD_BG_START = 0xFF101010.toInt()
private const val CARD_BG_END = 0xFF302F2A.toInt()
private const val CARD_TITLE_COLOR = 0xFF9AA0A6.toInt()
private const val CARD_VALUE_COLOR = 0xFFF5F5F4.toInt()

/** Папка в кэше, куда кладутся PNG-карточки для шаринга. */
private const val SHARE_DIR = "share"

/** Суффикс authority FileProvider. */
private const val PROVIDER_SUFFIX = ".fileprovider"
