package CodeSyS.Random_Number.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Хранилище сессий генерации.
 *
 * Все сессии отдаются отсортированными по убыванию [Session.lastUsedAt]
 * (последняя использованная — первая).
 */
interface SessionRepository {

    /** Наблюдение за списком сохранённых сессий (эмитит при каждом изменении). */
    fun observeSessions(): Flow<List<Session>>

    /** Однократное чтение списка сессий (для экспорта). */
    suspend fun observeSessionsOnce(): List<Session> = observeSessions().first()

    /** Возвращает сессию по [id] или `null`, если не найдена. */
    suspend fun getSession(id: String): Session?

    /**
     * Создаёт новую сессию.
     *
     * @return `false`, если сессия с таким [Session.id] уже есть —
     * существующая запись не перезаписывается.
     */
    suspend fun create(session: Session): Boolean

    /**
     * Обновляет уже существующую сессию.
     *
     * @return `false`, если сессии больше нет в хранилище (например,
     * её удалили с другого экрана) — «воскрешение» удалённой сессии
     * невозможно, и вызывающий код может отреагировать на это.
     */
    suspend fun update(session: Session): Boolean

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
