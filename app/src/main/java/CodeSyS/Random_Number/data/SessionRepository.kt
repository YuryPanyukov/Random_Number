package CodeSyS.Random_Number.data

import kotlinx.coroutines.flow.Flow

/**
 * Хранилище сессий генерации.
 *
 * Все сессии отдаются отсортированными по убыванию [Session.lastUsedAt]
 * (последняя использованная — первая).
 */
interface SessionRepository {

    /** Наблюдение за списком сохранённых сессий (эмитит при каждом изменении). */
    fun observeSessions(): Flow<List<Session>>

    /** Возвращает сессию по [id] или `null`, если не найдена. */
    suspend fun getSession(id: String): Session?

    /** Создаёт или обновляет сессию. */
    suspend fun save(session: Session)

    /** Удаляет сессию по [id]. Ничего не делает, если её нет. */
    suspend fun delete(id: String)

    /**
     * Сбрасывает ранее сгенерированные числа сессии (вариант «продолжить
     * текущую сессию, но скинуть выбранные числа»).
     *
     * @return обновлённая сессия или `null`, если не найдена.
     */
    suspend fun clearNumbers(id: String, at: Long): Session?
}
