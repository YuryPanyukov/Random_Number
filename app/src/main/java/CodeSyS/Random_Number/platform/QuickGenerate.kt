package CodeSyS.Random_Number.platform

import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionRepository
import CodeSyS.Random_Number.domain.SessionGenerationResult
import CodeSyS.Random_Number.domain.SessionGenerator
import CodeSyS.Random_Number.domain.createSessionGenerator

/**
 * «Быстрая генерация» без открытия приложения (задача 5.4).
 *
 * Берёт самую недавно использованную сессию, делает в ней один шаг
 * и сохраняет результат. Используется виджетом и плиткой быстрых настроек —
 * оба вызывают один и тот же код, поэтому ведут себя одинаково.
 *
 * @param repository хранилище сессий.
 * @param generator генератор по умолчанию (без seed).
 * @param clock источник времени — для детерминированных тестов.
 * @param generatorFactory создаёт генератор с учётом seed сессии.
 */
class QuickGenerate(
    private val repository: SessionRepository,
    private val generator: SessionGenerator = SessionGenerator(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val generatorFactory: (seed: Long?) -> SessionGenerator = { seed ->
        createSessionGenerator(seed, fallback = generator)
    },
) {

    /**
     * Генерирует один шаг в последней использованной сессии.
     *
     * @return обновлённая сессия или `null`, если сессий нет, диапазон
     * исчерпан, или сессию успели удалить.
     */
    suspend fun generateLatest(): Session? {
        val latest = repository.observeSessionsOnce().firstOrNull() ?: return null
        return generateIn(latest.id)
    }

    /**
     * Генерирует один шаг в сессии [id].
     *
     * @return обновлённая сессия или `null`, если её нет, диапазон
     * исчерпан, или сессию успели удалить.
     */
    suspend fun generateIn(id: String): Session? {
        val session = repository.getSession(id) ?: return null
        val result = generatorFactory(session.seed).generate(session)
        val values = when (result) {
            is SessionGenerationResult.Success -> result.values
            SessionGenerationResult.Exhausted -> return null
        }
        val updated = session.withGenerated(values, at = clock())
        // Сессию могли удалить, пока мы считали — тогда не «воскрешаем» её.
        return if (repository.update(updated)) updated else null
    }
}
