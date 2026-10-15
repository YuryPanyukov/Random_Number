package CodeSyS.Random_Number.platform

import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionPayload
import CodeSyS.Random_Number.data.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * Демо-сессии при первом запуске (идея из roadmap): вместо пустого экрана
 * пользователь сразу видит в меню три готовые сессии — числа, кубики и
 * монету. Демо создаются один раз: повторный вызов не дублирует их, а
 * при непустом хранилище (пользователь уже что-то создал) пропускается.
 *
 * Флаг «создано» живёт в настройках приложения, поэтому передаётся
 * снаружи ([demoSeeded]/[markDemoSeeded]) — класс остаётся без Android
 * и легко тестируется.
 *
 * @param repository хранилище сессий.
 * @param demoSeeded флаг «демо-сессии уже создавались».
 * @param markDemoSeeded помечает флаг созданным.
 * @param clock источник времени — для детерминированных тестов.
 */
class DemoSessions(
    private val repository: SessionRepository,
    private val demoSeeded: Flow<Boolean> = flowOf(false),
    private val markDemoSeeded: suspend () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Создаёт демо-сессии, если хранилище пусто и демо ещё не показывались.
     *
     * @return список созданных сессий (пустой, если ничего не создано).
     */
    suspend fun seedIfFirstRun(): List<Session> {
        if (demoSeeded.first()) return emptyList()
        markDemoSeeded()
        if (repository.observeSessionsOnce().isNotEmpty()) return emptyList()

        val now = clock()
        val sessions = listOf(
            Session.new(
                min = 1,
                max = 100,
                allowRepeats = false,
                now = now,
                title = "1..100, без повторов",
            ),
            Session.new(
                payload = SessionPayload.Dice(count = 2, sides = 6),
                now = now,
                title = "2d6",
            ),
            Session.new(
                payload = SessionPayload.Coin,
                now = now,
                title = "Монета",
            ),
        )
        // Все сессии должны вставиться; если что-то не вставилось
        // (гонка старых этапов) — откатываем, чтобы не оставить набор неполным.
        val created = mutableListOf<Session>()
        for (session in sessions) {
            if (repository.create(session)) {
                created.add(session)
            } else {
                created.forEach { repository.delete(it.id) }
                return emptyList()
            }
        }
        return created
    }
}
