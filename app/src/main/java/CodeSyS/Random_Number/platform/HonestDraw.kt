package CodeSyS.Random_Number.platform

import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionPayload
import CodeSyS.Random_Number.data.SessionRepository
import CodeSyS.Random_Number.domain.SessionGenerationResult
import CodeSyS.Random_Number.domain.SessionGenerator
import CodeSyS.Random_Number.domain.createSessionGenerator
import java.security.SecureRandom

/**
 * «Честный розыгрыш» в один тап (идея раздела 6).
 *
 * Отличается от обычной сессии так:
 *  - seed выбирается криптографическим источником ([SecureRandom]), а не
 *    системным ГПСЧ, и сохраняется в сессии — результат воспроизводим;
 *  - сессия помечается [Session.seedFromSecure], поэтому в шаринге
 *    (см. `buildShareText`) видны и seed, и источник;
 *  - первый розыгрыш выполняется сразу при создании: пользователю не нужно
 *    отдельно жать «Сгенерировать».
 *
 * Так как генерация детерминирована seed, любой может проверить итог:
 * достаточно создать сессию с теми же параметрами и задать этот seed.
 *
 * @param repository хранилище сессий.
 * @param seedSource источник seed (в тестах подменяется на детерминированный).
 * @param generatorFactory создаёт генератор по seed сессии.
 * @param clock источник времени — для детерминированных тестов.
 */
class HonestDraw(
    private val repository: SessionRepository,
    private val seedSource: () -> Long = { SecureRandom().nextLong() },
    private val generatorFactory: (seed: Long) -> SessionGenerator = { seed ->
        createSessionGenerator(seed)
    },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Разыгрывает одно число в диапазоне [min]..[max] (по умолчанию 1..100)
     * без повторов.
     *
     * @return созданная сессия с результатом или `null`, если сохранить её
     * не удалось.
     */
    suspend fun drawNumber(min: Int = MIN, max: Int = MAX): Session? = draw(
        Session.new(
            min = min,
            max = max,
            allowRepeats = false,
            now = clock(),
            title = TITLE,
            seed = seedSource(),
            seedFromSecure = true,
        ),
    )

    /**
     * Разыгрывает одного участника из списка [items] (без повторов).
     *
     * @return созданная сессия с результатом или `null`, если список пуст
     * либо сессию сохранить не удалось.
     */
    suspend fun drawItem(items: List<String>): Session? {
        if (items.isEmpty()) return null
        return draw(
            Session.new(
                payload = SessionPayload.Items(items = items, allowRepeats = false),
                now = clock(),
                title = LIST_TITLE,
                seed = seedSource(),
                seedFromSecure = true,
            ),
        )
    }

    /**
     * Создаёт [session] и сразу выполняет в ней первый розыгрыш.
     *
     * @return сессия с записанным результатом; [session] без результата,
     * если диапазон/список уже исчерпан (для новой сессии невозможно);
     * `null`, если создать или обновить сессию не удалось.
     */
    private suspend fun draw(session: Session): Session? {
        if (!repository.create(session)) return null
        val seed = session.seed ?: return session
        val values = when (val result = generatorFactory(seed).generate(session)) {
            is SessionGenerationResult.Success -> result.values
            SessionGenerationResult.Exhausted -> return session
        }
        val updated = session.withGenerated(values, at = clock())
        return if (repository.update(updated)) updated else session
    }

    companion object {
        /** Нижняя граница розыгрыша числа. */
        const val MIN = 1

        /** Верхняя граница розыгрыша числа. */
        const val MAX = 100

        /** Заголовок сессии-розыгрыша числа. */
        const val TITLE = "Честный розыгрыш"

        /** Заголовок сессии-розыгрыша участника из списка. */
        const val LIST_TITLE = "Честный розыгрыш (список)"
    }
}
