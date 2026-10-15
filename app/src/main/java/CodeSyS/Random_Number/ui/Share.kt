package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.data.DefaultSessionTexts
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionTexts
import android.content.Context
import android.content.Intent

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
