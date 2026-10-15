package CodeSyS.Random_Number.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Сохраняет текст в файл через системный диалог. */
fun interface DocumentSaver {
    /**
     * Предлагает сохранить [text] в файл [fileName] с MIME-типом [mimeType].
     *
     * Если системный диалог недоступен, текст уходит в share-sheet.
     */
    fun save(text: String, fileName: String, mimeType: String)
}

/**
 * Хук сохранения текста в файл через `ACTION_CREATE_DOCUMENT`.
 *
 * Важно: [android.content.Intent.EXTRA_TEXT] системный диалог не обрабатывает
 * — он лишь возвращает `Uri` созданного документа. Поэтому полученный `Uri`
 * здесь же записывается содержимым ([writeToDocument]); без этого файл
 * оставался бы пустым.
 */
@Composable
fun rememberDocumentSaver(): DocumentSaver {
    val context = LocalContext.current
    // Текст, ожидающий выбранного пользователем файла.
    var pending by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = pending
        pending = null
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && text != null) {
            writeToDocument(context, uri, text)
        }
    }

    return remember(context, launcher) {
        DocumentSaver { text, fileName, mimeType ->
            val intent = createSaveDocumentIntent(text, fileName, mimeType)
            if (intent == null) {
                shareText(context, text)
            } else {
                pending = text
                launcher.launch(intent)
            }
        }
    }
}

/**
 * Записывает [text] в документ [uri] (UTF-8).
 *
 * @return `true`, если запись прошла успешно.
 */
fun writeToDocument(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(text.toByteArray(Charsets.UTF_8))
    } != null
}.getOrDefault(false)
