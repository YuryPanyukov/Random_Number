# main_mind.md — Идея и план приложения «Случайные числа»

## 1. Идея (сохранить)

**Случайные числа** — Android-приложение (Kotlin, Jetpack Compose) для генерации случайных чисел
в заданном диапазоне с возможностью исключить повторы и сохранять «сессии» генерации.

Ключевая суть:
- В **главном меню** пользователь либо начинает **новую сессию** генерации, либо **продолжает предыдущую**.
- **Сессия** описывается параметрами: диапазон (от…до) и флаг «без повторений».
- Если включено «без повторений», ранее выданные числа не выдаются снова.
- Когда в режиме «без повторений» выбраны **все** числа диапазона — приложение сообщает
  «Все числа выбраны» и предлагает: создать новую сессию **или** продолжить текущую со сбросом
  ранее выбранных чисел.
- Сессии **хранятся** и показываются при запуске приложения; сессии можно **удалять**.

## 2. Технологии и стек (текущий проект)

| Компонент | Решение |
|---|---|
| Язык | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material3 (Compose BOM `2026.02.01`) |
| Мин. SDK | 24, target/compile 37 |
| Хранение сессий | DataStore (Preferences) + kotlinx.serialization JSON — альтернатива: Room |
| Асинхронность | Kotlin Coroutines + Flow (`lifecycle-runtime-ktx` уже подключён) |
| Навигация | `navigation-compose` (2 экрана) или простой sealed-состояния NavHost |
| DI | без тяжёлых фреймворков — ручная сборка/`ViewModelProvider.Factory` |
| Тесты | JUnit4 (unit), kotlinx-coroutines-test, Compose UI Test (уже в зависимостях) |

Новые зависимости (добавить в `gradle/libs.versions.toml` + `app/build.gradle.kts`):
`navigation-compose`, `kotlinx-serialization-json` (+ плагин `kotlin.plugin.serialization`),
`datastore-preferences`, `lifecycle-viewmodel-compose`, `kotlinx-coroutines-test`,
`turbine` (опционально, для Flow).

## 3. Архитектура (MVVM + Clean-lite)

```
app/src/main/java/CodeSyS/Random_Number/
├── data/
│   ├── Session.kt              // модель домена (id, name, min, max, allowRepeats, generatedNumbers, createdAt)
│   ├── SessionRepository.kt    // интерфейс: observeAll(), save(), delete(), clearNumbers()
│   ├── DataStoreSessionRepository.kt // реализация (JSON в DataStore)
│   └── SessionSerializer.kt    // DTO <-> домен
├── domain/
│   └── NumberGenerator.kt      // чистая логика генерации (инжектируемый Random)
├── ui/
│   ├── theme/                  // Color.kt, Theme.kt, Type.kt
│   ├── navigation/AppNav.kt
│   ├── home/HomeScreen.kt + HomeViewModel.kt      // главное меню, список сессий
│   └── session/SessionScreen.kt + SessionViewModel.kt // экран генерации
└── MainActivity.kt
```

### Доменная модель
```kotlin
data class Session(
    val id: String,              // UUID
    val title: String,           // "Сессия от 28.09 12:00"
    val min: Int,                // от
    val max: Int,                // до
    val allowRepeats: Boolean,   // true = повторы разрешены
    val generated: List<Int>,    // история выданных чисел
    val createdAt: Long,
    val lastUsedAt: Long,
)

enum class GenerationResult { SUCCESS, EXHAUSTED } // EXHAUSTED = все числа выбраны
```

### Алгоритм генерации (`NumberGenerator`)
- `next(session): Pair<Int, GenerationResult>` (или data-класс `NextNumber`).
- Повторы разрешены → `random.nextInt(min, max + 1)` (или `min..max.random()`).
- Без повторений:
  - вычислить множество доступных = `(min..max) - generated.toSet()`;
  - если пусто → `EXHAUSTED` (UI показывает диалог «Все числа выбраны»);
  - иначе случайно выбрать из доступных (доступ к `Random` — через конструктор → детерминированные тесты).
- Ограничение размера диапазона: `min <= max`, иначе ошибка валидации; при `!allowRepeats`
  размер диапазона <= 100 000 (защита от переполнения памяти) — валидация на экране создания.
- `reset(session)` → очистить `generated` (вариант «продолжить, но сбросить»).

### ViewModel-состояния
- `HomeViewModel`: `StateFlow<HomeUiState>` — список сессий (сортировка по `lastUsedAt desc`),
  пустое состояние «Нет сессий».
- `SessionViewModel`: `StateFlow<SessionUiState>` — параметры, последние число(а),
  счётчик «выбрано X из Y», флаг `showExhaustedDialog`.
- Обработчики: `generate()`, `resetNumbers()`, `startNewSession(min, max, allowRepeats)`,
  `deleteSession(id)`.

## 4. Экраны и UX-поток

1. **HomeScreen (главное меню)**
   - Кнопка «➕ Новая сессия» → экран создания/настройки.
   - Раздел «Продолжить»: список сохранённых сессий (последняя сверху): заголовок,
     диапазон, флаг «без повторов», счётчик `X/Y`, дата.
   - Свайп/длинное нажатие → «Удалить» (с подтверждением).
   - Запуск приложения → сразу показ сохранённых сессий (загрузка из DataStore).
2. **NewSessionScreen** (диалог или отдельный экран): поля «От», «До», switch
   «Без повторений», кнопки «Создать» / «Отмена». Валидация ввода.
3. **SessionScreen (генерация)**: крупное число в карточке, кнопка «Сгенерировать»,
   история последних чисел (горизонтальный чип-лист), прогресс `X/Y` (только для
   «без повторений»), кнопки «Сбросить числа», «В меню».
   - При `EXHAUSTED` → AlertDialog: **«Все числа выбраны!»** + кнопки
     «Новая сессия» и «Продолжить со сбросом».

## 5. Дизайн

### Иконки
- **Лаунчер** — адаптивная иконка (`mipmap-anydpi-v26` уже есть):
  - foreground: векторный кубик (dice) с пипками в виде цифр / либо «%»-символ,
    safe zone 66×66dp, белый на акцентном фоне;
  - background: сплошной градиент (индиго → фиолет);
  - альтернатива: стилизованная «7» с искрами.
- **Внутри приложения** — Material Icons (или переиспользованные векторные):
  `Add` (новая сессия), `Delete`/`DeleteOutline` (удалить), `Replay` (сброс),
  `Casino` (сгенерировать), `History` (история), `ArrowBack`, `Tune` (параметры).
- Все вектора — `res/drawable/*.xml` (pathData), без растровых PNG.

### Тема (Material 3, светлая + тёмная + dynamic color)
- **Идея палитры «casino/lottery»**: основной — индиго/фиолетовый, акцент — золотисто-жёлтый
  («выигрыш»), нейтральный фон — тёмно-сине-серый в тёмной теме.
- `Color.kt`: `Indigo40/80`, `Violet40/80`, `Amber40/80` (акцент), `Surface` для карточек.
- `Theme.kt`: заменить дефолтные Purple/Pink на новые палитры, `dynamicColor = true`
  на Android 12+ (оставить), `Typography` — крупный стиль для числа-результата
  (`displayLarge` моноширинный/жирный).
- Ключевой элемент: число в `SessionScreen` — `displayLarge`, акцентный цвет,
  анимация «подброса» (простая смена/AnimateContent).

## 6. Этапы реализации (порядок работ)

1. **Этап 0 — инфраструктура**: добавить зависимости (serialization, datastore, navigation,
   coroutines-test), подключить плагин serialization.
2. **Этап 1 — домен**: `Session`, `GenerationResult`, `NumberGenerator` (+ unit-тесты сразу).
3. **Этап 2 — данные**: `SessionRepository` (интерфейс) + DataStore-реализация + fake для тестов.
4. **Этап 3 — ViewModel**: `HomeViewModel`, `SessionViewModel` (+ unit-тесты с fake repo/generator).
5. **Этап 4 — UI**: theme (цвета/типографика), `HomeScreen`, `SessionScreen`, навигация,
   диалог «Все числа выбраны», удаление сессий.
6. **Этап 5 — иконки**: адаптивная лаунчер-иконка (вектор), строковые ресурсы.
7. **Этап 6 — тесты и полировка**: UI-тесты, edge-to-edge, пустые состояния, проверка `./gradlew test`.

## 7. План тестов

### Unit-тесты (`app/src/test/.../`)
- `NumberGeneratorTest`
  - `repeatsAllowed_returnsValueInRange` (1000 итераций — все в `min..max`);
  - `noRepeats_neverReturnsDuplicate` (диапазон 1..10 — выдаёт 10 уникальных);
  - `noRepeats_returnsExhausted_whenAllPicked` (после N генераций → `EXHAUSTED`);
  - `repeatsAllowed_neverExhausted` (даже после >N итераций — `SUCCESS`);
  - `fixedSeed_isDeterministic` (фиксированный `Random(seed)` → одинаковая последовательность);
  - `singleValueRange_works` (min == max);
  - `invalidRange_throws` (min > max).
- `SessionRepositoryTest` (с fake/ин-мем репозиторием)
  - сохранение/загрузка сессии, сортировка по `lastUsedAt`, `delete`, `clearNumbers`.
- `HomeViewModelTest`, `SessionViewModelTest` (kotlinx-coroutines-test + Turbine)
  - загрузка списка при старте, удаление сессии из UI-состояния,
  - `generate()` обновляет число и счётчик, `showExhaustedDialog` выставляется
    в правильный момент, `resetNumbers()` очищает историю.

### UI-тесты (`app/src/androidTest/.../`)
- `HomeScreenTest`: отображение списка сессий, пустое состояние, кнопка «Новая сессия».
- `SessionScreenTest`: генерация по тапу, появление диалога «Все числа выбраны»,
  кнопки «Новая сессия» / «Продолжить со сбросом», удаление сессии.

Критерий готовности: `./gradlew test` зелёный, ручной прогон сценария
«новая сессия 1..10 без повторов → 10 генераций → диалог → сброс → продолжение».

## 8. Риски / открытые вопросы

- Выбор хранилища: DataStore+JSON (проще, без KSP) vs Room (масштабируемее) → начать с DataStore.
- Ограничение размера диапазона в режиме «без повторений» (память) — согласовать лимит.
- Сохранение полной истории чисел может раздувать JSON → хранить только последние N (например, 200)
  + счётчик общего числа выданных; либо хранить `generated` как множества/bitmap.
