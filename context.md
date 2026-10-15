# План реализации (по new_mind.md)

Проект: Android-приложение «Random_Number» (Kotlin, Jetpack Compose, MVVM).
Текущее ядро: `data/Session`, `domain/NumberGenerator`, репозитории (`DataStoreSessionRepository` / `InMemorySessionRepository`), навигация `ui/AppNav` (Home → NewSession → Session).

> Статус: план согласован. **Этап 1 ВЫПОЛНЕН** (2026-09-29): все 10 задач реализованы,
> `./gradlew test` — 78 тестов, 0 падений; `assembleDebug` собирается.
> **2.0 ВЫПОЛНЕНА** (2026-10-01): абстракция режимов — 200 тестов, 0 падений.
> **2.1 ВЫПОЛНЕНА** (2026-10-03): случайный элемент из списка — 247 тестов, 0 падений.
> **2.2 ВЫПОЛНЕНА** (2026-10-03): бросок кубиков — 265 тестов, 0 падений.
> **2.3 ВЫПОЛНЕНА** (2026-10-03): монетка — 286 тестов, 0 падений.
> **2.4 ВЫПОЛНЕНА** (2026-10-03): перемешивание списка — 312 тестов, 0 падений.
> **2.6 ВЫПОЛНЕНА** (2026-10-03): экран выбора режима — 315 тестов, 0 падений.
> **2.5 ВЫПОЛНЕНА** (2026-10-05): экспорт истории (Markdown + фикс сохранения в файл
> + share-sheet) — 356 тестов, 0 падений.
> Также выполнены (2026-10-05): честный розыгрыш одним тапом (раздел 6 `next.md`),
> локализация заголовков/служебных строк, дублирование сессии (3.3) и ярлыки (3.14),
> теги/избранное/архив/поиск (3.5–3.7) — 379 тестов, 0 падений.
> Остальные задачи Этапов 3–4 — только документ; к выполнению не приступали.

## Шкала сложности

| Оценка | Значение | Ориентир |
|--------|----------|----------|
| **XS** | < 1 часа | правка в одном файле, без миграций |
| **S** | 1–3 часа | один класс/экран + тесты |
| **M** | 0.5–1 день | несколько файлов, домен + UI + тесты |
| **L** | 2–3 дня | новый модуль, миграции данных или внешние API |
| **XL** | 4+ дней | инфраструктурная задача (Drive, App Actions, полная переработка модели) |

Итоговая оценка этапа — сумма задач + ~20% на интеграцию и тесты между задачами.

---

## Этап 1 — Усилить ядро

Цель: довести текущий сценарий «генерация чисел» до удобного ежедневного инструмента.
**Итого: ~5–6 дней**

| # | Задача | Файлы | Сложность |
|---|--------|-------|-----------|
| 1.1 | Батч-генерация: `NumberGenerator.next(count, …)` с корректной обработкой частичного исчерпания; `Session.withGenerated(List<Int>)`; выбор N (2/3/5/10) в `SessionScreen` | `NumberGenerator.kt`, `Session.kt`, `SessionScreen.kt`, `SessionViewModel.kt` + тесты | **M** |
| 1.2 | Отмена последнего числа: `Session.withoutLastGenerated(at)`, кнопка disabled при пустой истории | `Session.kt`, `SessionViewModel.kt`, `SessionScreen.kt` + тесты | **S** |
| 1.3 | Копирование: долгое нажатие на число → меню «Копировать» (`ClipboardManager`) | `SessionScreen.kt` | **XS** |
| 1.4 | Шаринг: `Intent.ACTION_SEND` со списком чисел сессии | `SessionScreen.kt` (+ возможно новый файл Share.kt) | **S** |
| 1.5 | Пресеты диапазонов: источник данных (domain) + быстрый тап на `NewSession` | новый `domain/Presets.kt`, `NewSessionScreen.kt` | **S** |
| 1.6 | Ручное редактирование истории: `removeGeneratedAt(i)`, `addManual(n)` с валидацией; UI удаления и ручного ввода | `Session.kt`, `SessionViewModel.kt`, `SessionScreen.kt` + тесты | **M** |
| 1.7 | Тёмная/светлая тема вручную (FollowSystem/Light/Dark) + выбор акцентного цвета; хранение в DataStore | `ui/theme/Theme.kt`, `Color.kt`, новый `SettingsRepository` | **M** |
| 1.8 | Вибрация и звук: обёртка `FeedbackProvider`, вызов при генерации | новый `FeedbackProvider.kt`, `SessionViewModel.kt` | **S** |
| 1.9 | Экран настроек: тема, акцент, вибрация/звук; маршрут в `AppNav` | новый `SettingsScreen.kt`, `AppNav.kt` | **M** |
| 1.10 | Вынос строк в `res/values/strings.xml` (ru + заготовка en) | все UI-файлы | **S** |

**Критерий готовности:** все «быстрые победы» работают, `./gradlew test` зелёный. — ✅ **Выполнено 2026-09-29**: 78 unit-тестов зелёные, debug-сборка проходит.

### Журнал изменений этапа 1

| # | Что сделано | Файлы | Отклонения от плана |
|---|-------------|-------|---------------------|
| 1.1 | Перегрузка `NumberGenerator.next(count, min, max, …)` возвращает `BatchGenerationResult.Success(numbers, partial)` / `Exhausted` (частичное исчерпание = оставшиеся числа + `partial=true`); `Session.withGenerated(List<Int>)`; в UI — чипы N; генерация в VM едина для N=1 и батча | `NumberGenerator.kt`, `Session.kt`, `SessionViewModel.kt`, `SessionScreen.kt`, `NumberGeneratorTest`, `SessionViewModelTest`, `SessionTest` | В список N добавлен «1» (иначе одиночная генерация недостижима) |
| 1.2 | `Session.withoutLastGenerated(at)`, VM `undoLast()`, кнопка «Убрать последнее» (disabled при пустой истории) | `Session.kt`, `SessionViewModel.kt`, `SessionScreen.kt`, тесты | — |
| 1.3 | Долгое нажатие на крупное число и на число истории → `DropdownMenu` с «Копировать» (`LocalClipboardManager`) | `SessionScreen.kt` | — |
| 1.4 | Кнопка «Поделиться»: `Intent.ACTION_SEND` со списком чисел | новый `ui/Share.kt`, `SessionScreen.kt` | — |
| 1.5 | enum `RangePreset` (Кубик/Монета/Лотерея/Сотня), чипы на форме новой сессии | новый `domain/RangePresets.kt`, `NewSessionScreen.kt` | Названия — в ресурсах (не в domain), ради i18n; у Кубика/Монеты повторы разрешены |
| 1.6 | `Session.removeGeneratedAt(i)` / `Session.addManual(n)` с валидацией (диапазон, повторы); меню «Удалить» у числа; поле ручного ввода с ошибками | `Session.kt`, `SessionViewModel.kt`, `SessionScreen.kt`, тесты | Ошибка валидации — в UI (VM молча игнорирует некорректный ввод) |
| 1.7 | `SettingsRepository` + `DataStoreSettingsRepository` (отдельный DataStore «settings»); `ThemeMode` SYSTEM/LIGHT/DARK; акценты DYNAMIC/INDIGO/TEAL/ROSE; тема читает настройки в `MainActivity` | новый `data/SettingsRepository.kt`, `data/DataStoreSettingsRepository.kt`, `ui/theme/Theme.kt`, `Color.kt`, `MainActivity.kt` | Динамическая тема — только при акценте «Динамический» (Android 12+) |
| 1.8 | `FeedbackProvider` + `SystemFeedbackProvider` (вибрация `VibrationEffect`/`VibratorManager`, звук `ToneGenerator`), флаги кешируются из `SettingsRepository`; вызов из `SessionViewModel.generate()` | новый `FeedbackProvider.kt`, `App.kt`, `SessionViewModel.kt`, `AndroidManifest.xml` | Добавлено разрешение `VIBRATE` |
| 1.9 | `SettingsScreen` (тема/акцент/вибрация/звук), `SettingsViewModel`, маршрут `settings` в `AppNav`, шестерёнка на Home | новый `ui/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`, `AppNav.kt`, `HomeScreen.kt`, `ViewModelFactories.kt` | — |
| 1.10 | Новые строки (батч, отмена, копирование/шаринг, пресеты, настройки, ошибки ред.) в `values/strings.xml`; полный английский перевод в новом `values-en/strings.xml` | `strings.xml`, новый `values-en/strings.xml`, все новые UI-коды | en — не пустая заготовка, а перевод |

Новые тесты: `SessionTest` (11), расширены `NumberGeneratorTest` (+8 батч) и `SessionViewModelTest` (+10), новый `SettingsViewModelTest` (5). Итого: **78 тестов, 0 падений**.

---

## Этап 2 — Расширить сценарии

Цель: выйти за пределы «числа в диапазоне».
**Итого: ~6–7 дней**

Предварительная задача (упрощает всё дальше):

| # | Задача | Файлы | Сложность |
|---|--------|-------|-----------|
| 2.0 | Абстракция режима генерации: `GenerationMode` / обобщение сессии под «числа» и «элементы» — единый контракт для новых экранов — ✅ **выполнена 2026-10-01** как `SessionPayload` (см. журнал ниже) | `Session.kt`, `SessionCodec.kt`, `SessionPayload.kt` + тесты | **L** |

| # | Задача | Файлы | Сложность |
|---|--------|-------|-----------|
| 2.1 | Случайный элемент из списка: ввод списка, режим «без повторений», переиспользование идеи `pickUnique` — ✅ **выполнена 2026-10-03** | новый domain + форма/экран | **M** |
| 2.2 | Бросок кубиков: `DiceRoller(count, sides)`, сумма, история, анимация — ✅ **выполнена 2026-10-03** | `domain/DiceRoller.kt` (был), режим на общем экране | **M** |
| 2.3 | Монетка: орёл/решка, крупная анимация, статистика — ✅ **выполнена 2026-10-03** | `domain/CoinFlipper.kt` (новый), режим на общем экране | **S** |
| 2.4 | Перемешивание списка: Fisher–Yates через внедрённый `Random` — ✅ **выполнена 2026-10-03** | переиспользовать экран 2.1 + domain | **S** |
| 2.5 | Экспорт истории: CSV/TXT/JSON/Markdown с таймстампами, `ACTION_CREATE_DOCUMENT` / share-sheet — ✅ **выполнена 2026-10-05** | `SessionCodec.kt`, новый `HistoryExporter.kt`, `ui/DocumentSaver.kt` + тесты | **M** |
| 2.6 | Новые маршруты в `AppNav`, общая навигация «режимов» — ✅ **выполнена 2026-10-03** | `AppNav.kt` | **S** |

**Критерий готовности:** 4+ режима работают, история каждого сохраняется и экспортируется.

### Журнал изменений этапа 2

| # | Что сделано | Файлы | Отклонения от плана |
|---|-------------|-------|---------------------|
| 2.6 | Экран выбора режима. Новый stateless `ModePickerScreen` (карточки по `NewSessionMode.entries` с названием и пояснением) — первый шаг создания сессии. Навигация: Home → `mode_picker` → `new_session/{mode}` → `session/{id}`; `NewSessionViewModel` принимает `initialMode` (фабрика `newSessionViewModelFactory(mode)`), поэтому форма открывается сразу с нужными полями. При создании сессии из стека убираются и форма, и выбор режима (`popUpTo(MODE_PICKER)`). Чипы режимов на форме оставлены для быстрого переключения. +3 теста на `initialMode` | `ui/ModePickerScreen.kt` (новый), `ui/AppNav.kt`, `ui/NewSessionScreen.kt`, `ui/ViewModelFactories.kt`, `ui/newsession/NewSessionViewModel.kt`, `res/values*/strings.xml` + тесты | Экран без собственной ViewModel: список режимов фиксирован, состояние не нужно. `initialMode` передан аргументом конструктора VM (не через SavedStateHandle), чтобы не тянуть `viewModelFactory` на Android-контекст |
| 2.4 | Режим «перемешивание списка». Новый `domain/Shuffler` — тасование Фишера–Йетса (`O(n)`) поверх внедрённого `Random`, поэтому seed воспроизводим. `SessionPayload.Shuffle(items)` (`type = "shuffle"`, непустой список); журнал хранит индексы в порядке перемешивания, поэтому история/экспорт/вывод общие с режимом списка. `Session`: `isShuffleMode`, `min=0`/`max=lastIndex`/`allowRepeats=true`, `items` теперь читает и `Items`, и `Shuffle`; `lastShuffle` — последние `items.size` записей, `lastDisplay` — перестановка через «→». `SessionViewModel.generateShuffle` тасует индексы и пишет их одной записью за нажатие (`batchSize` не применяется). Форма: пятый режим «Перемешать» (нужно ≥ 2 элементов). Экран: перестановка под крупным значением, ряд батча/статистика/«Убрать последнее» для перемешивания скрыты. Экспорт: строка «N элементов», значения — элементы. +26 тестов (`ShufflerTest`, `SessionShuffleTest`, кейсы Shuffle в экспорте/VM/NewSession) | `domain/Shuffler.kt` (новый), `data/SessionPayload.kt`, `data/Session.kt`, `data/HistoryExporter.kt`, `ui/session/SessionViewModel.kt`, `ui/SessionScreen.kt`, `ui/newsession/NewSessionViewModel.kt`, `ui/NewSessionScreen.kt`, `res/values*/strings.xml` + тесты | Перестановка — атомарная запись (все элементы одного тасования пишутся вместе): убрать один элемент нельзя, поэтому кнопка «Убрать последнее» для перемешивания отключена |
| 2.3 | Режим «монета». Новый `domain/CoinFlipper` + `CoinSide` (HEADS/TAILS, ordinal 0/1, label «Орёл»/«Решка»). `SessionPayload.Coin` (`data object`, `type = "coin"`); журнал хранит стороны 0/1, поэтому история/экспорт/вывод общие. `Session`: `isCoinMode`, `min=0`/`max=1`/`rangeSize=2`/`allowRepeats=true`, `displayOf` → стороны, `coinHeadsCount`/`coinTailsCount`. `SessionViewModel.generateCoin` бросает `batchSize` монет. Форма: четвёртый режим «Монета» (без полей). Экран: крупная сторона + счёт «Орёл: X · Решка: Y» вместо `SessionStatsCard`. Экспорт: строка «Орёл/Решка», значения — стороны. +21 тест (`CoinFlipperTest`, `SessionCoinTest`, кейсы Coin в экспорте/VM) | `domain/CoinFlipper.kt` (новый), `data/SessionPayload.kt`, `data/Session.kt`, `data/HistoryExporter.kt`, `ui/session/SessionViewModel.kt`, `ui/SessionScreen.kt`, `ui/newsession/NewSessionViewModel.kt`, `ui/NewSessionScreen.kt`, `res/values*/strings.xml` + тесты | Названия сторон захардкожены в `CoinSide` (как заголовки сессий), не переведены в `strings.xml`; анимации пока нет — только показ/счёт |
| 2.2 | Режим «кубики». `SessionPayload.Dice(count, sides)` (`type = "dice"`, `label = "2d6"`), встроен в общий экран. `Session`: `isDiceMode`/`isNumbersMode`, `min = 1`, `max = sides`, `allowRepeats = true`, `lastDiceRoll` (последние `count` записей) и `lastDisplay` = сумма броска. `SessionViewModel.generateDice` бросает `DiceRoller` и пишет каждое значение кубика отдельной записью журнала; seed — как у чисел; `batchSize` не применяется. Форма: третий режим «Кубики» (поля «Сколько кубиков/граней», переключатель повторов скрыт). Экран сессии: сумма крупно + детализация «4 + 2 + 6», ряд батча для кубиков скрыт. Экспорт: строка-описание = `2d6`, значения — выпавшие числа. +18 тестов (`SessionDiceTest`, кейсы Dice в `HistoryExporterTest`, VM-тесты) | `data/SessionPayload.kt`, `data/Session.kt`, `data/HistoryExporter.kt`, `ui/session/SessionViewModel.kt`, `ui/SessionScreen.kt`, `ui/newsession/NewSessionViewModel.kt`, `ui/NewSessionScreen.kt`, `res/values*/strings.xml` + тесты | Домен `DiceRoller` уже существовал и был покрыт тестами — добавлена только интеграция; неизвестное ограничение: история кубиков в UI/экспорте пока плоская, без группировки по броскам |
| 2.1 | Режим «элемент из списка». Новый `domain/ItemGenerator` — выбор элементов поверх `NumberGenerator` над индексами (`0..items.lastIndex`), поэтому режим «без повторов» и частичное исчерпание переиспользуют `pickUnique` без дублирования. `SessionPayload.Items(items, allowRepeats)` (не пустой список) с дискриминатором `type = "items"`. Журнал для списка хранит **индексы** элементов, а показ идёт через `Session.displayOf` / `generatedDisplay` / `lastDisplay`: генерация, прогресс `X/Y` и `isExhausted` работают одинаково для обоих режимов. Свойства `min`/`max`/`allowRepeats`/`rangeSize` стали полиморфными (`0..lastIndex` для списка). Новая сессия: переключатель «Числа/Список» в форме, ввод элементов (строки, пустые/дубликаты отбрасываются). UI сессии: крупное значение и история показывают элементы, ручной ввод и статистика скрыты для списка. Экспорт TXT/CSV выводит элементы (с экранированием `;` и кавычек), шаринг — значения. +47 тестов (`ItemGeneratorTest`, `SessionItemsTest`, кейсы Items в `HistoryExporterTest`, VM-тесты). Обратная совместимость: старые записи без `type` читаются как `Numbers` (путь не изменён) | `domain/ItemGenerator.kt` (новый), `data/SessionPayload.kt`, `data/Session.kt`, `data/HistoryExporter.kt`, `ui/newsession/NewSessionViewModel.kt`, `ui/NewSessionScreen.kt`, `ui/session/SessionViewModel.kt`, `ui/SessionScreen.kt`, `ui/Share.kt`, `res/values*/strings.xml` + тесты | Журнал списка хранит индексы, а не строки: это сохранило тип `GeneratedEntry.value: Int` и всю существующую кодовую базу/тесты без миграции формата. Экран сессии оставлен единым (общий каркас + режимозависимый показ), отдельный экран не плодился |
| 2.0 | Новый sealed-тип `SessionPayload` (`@SerialName("numbers")` — `Numbers(min, max, allowRepeats)` с `size: Long`). `Session` переведён на `payload` (primary-конструктор), плоский конструктор `Session(id, title, min, max, allowRepeats, …)` оставлен secondary — существующий код и ~40 мест в тестах не изменились. Свойства `min`/`max`/`allowRepeats` делегируют `Numbers`. `SessionCodec`: `classDiscriminator = "type"`, нормализация старых записей (`generated` → `log`, корневые `min/max/allowRepeats` → `payload`) перед декодированием; fallback для обрезанных записей — минимальный `Numbers(1, 1, true)`. `SessionTransfer`: bundle разбирается поэлементно через `SessionCodec` — старые экспортные файлы читаются. `Session.new(payload, now, title, seed)` — фабрика для будущих режимов. +7 тестов: round-trip payload, оба legacy-формата, их сочетание, старый bundle, fallback, текущий JSON | `Session.kt`, `SessionPayload.kt` (новый), `SessionCodec.kt`, `SessionTransfer.kt`, `SessionCodecTest.kt`, `HistoryExporterTest.kt` | Назван `SessionPayload`, а не `GenerationMode`: это данные сессии, а не enum режимов; число режимов не фиксировано. Домен (`NumberGenerator`) не тронут — обобщение контракта генерации отнесено к задачам 2.1–2.4, когда появится второй режим |

---

## Этап 3 — Интеграции и удержание

Цель: вернуть пользователя в приложение, сделать сессии удобными для жизни.
**Итого: ~8–10 дней**

| # | Задача | Файлы | Сложность |
|---|--------|-------|-----------|
| 3.1 | Модель истории с таймстампами: `generated: List<Int>` → `List<GeneratedEntry(value, at)>` + миграция в `SessionCodec` с обратной совместимостью (сделать **раньше**, чем 3.7) | `Session.kt`, `SessionCodec.kt`, все, кто читает `generated` + тесты | **L** |
| 3.2 | Редактирование сессии: изменить диапазон/режим/название без создания новой | `Session.kt`, `SessionViewModel`/Home VM, UI формы | **M** |
| 3.3 | Дублирование сессии: `duplicate()` — копия с настройками, пустая история; меню долгого тапа на Home — ✅ **выполнена 2026-10-05** | `Session.kt`, `HomeScreen.kt`, `HomeViewModel.kt` | **S** |
| 3.4 | Названия и заметки: поле `description` + редактирование | `Session.kt`, форма сессии, codec | **S** |
| 3.5 | Теги/избранное: `tags: List<String>`, `isFavorite`; UI чипов — ✅ **выполнена 2026-10-05** | `Session.kt`, `HomeScreen.kt`, codec + тесты | **M** |
| 3.6 | Архив: `isArchived`, фильтр «Архив» на Home — ✅ **выполнена 2026-10-05** | `Session.kt`, `HomeViewModel.kt`, `HomeScreen.kt` | **S** |
| 3.7 | Поиск и сортировка на Home (дата/название/прогресс) — ✅ **выполнена 2026-10-05** | `HomeViewModel.kt`, `HomeScreen.kt` | **S** |
| 3.8 | Импорт/экспорт сессий (JSON-файл): пакетный codec, валидация, конфликт id | `SessionCodec.kt`, новый экспортёр + тесты | **M** |
| 3.9 | Статистика по сессии: `SessionStats` (частое/редкое, среднее, мин/макс, остаток) — чистая функция | новый `domain/SessionStats.kt`, `SessionScreen.kt` + тесты | **S** |
| 3.10 | Гистограмма распределения (Compose Canvas, без сторонних библиотек) | новый `ui/Histogram.kt` | **M** |
| 3.11 | Фильтр и поиск по истории (чётные/>50/поиск) + показ таймстампов | `SessionScreen.kt` (опирается на 3.1) | **M** |
| 3.12 | Виджет на рабочий стол (Glance): кнопка «Сгенерировать», показ результата, обновление при генерации | новый пакет `widget/`, `AndroidManifest.xml`, хук в VM | **L** |
| 3.13 | Плитка в быстрых настройках (`TileService`) | новый сервис + манифест | **S** |
| 3.14 | Ярлыки сессий (`ShortcutManager`) — долгий тап по иконке — ✅ **выполнена 2026-10-05** | `res/xml/shortcuts.xml`, `AppNav.kt`/`MainActivity.kt` | **S** |

**Критерий готовности:** сессии масштабируются на десятки штук, виджет/плитка/ярлык работают.

---

## Этап 4 — Продвинутое

Цель: «честность», безопасность, автоматизация.
**Итого: ~10–12 дней** (без Drive — см. 4.11)

| # | Задача | Файлы | Сложность |
|---|--------|-------|-----------|
| 4.1 | Seed / воспроизводимость: `seed: Long?` в сессии, `Random(seed)`, UI-поле и индикатор | `NumberGenerator.kt`, `Session.kt`, UI + тесты | **M** |
| 4.2 | SecureRandom («Безопасный режим»): фабрика источника случайности | `NumberGenerator.kt` (фабрика), настройки | **S** |
| 4.3 | Взвешенный рандом: `WeightedPicker(items, weights)`, редактирование весов | новый domain + UI | **M** |
| 4.4 | Случайная дата/время в диапазоне | новый domain + экран | **S** |
| 4.5 | Случайный цвет HEX/RGB с копированием и историей | новый domain + экран | **S** |
| 4.6 | Генератор паролей/PIN: длина, наборы символов, без повторов | новый domain + экран + тесты | **M** |
| 4.7 | Буква/слого/слово и дробные числа (шаг, знаки после запятой) | новый domain + экраны | **S** |
| 4.8 | Ограничения на генерацию: сумма, чётность, исключения, сортировка | `NumberGenerator.kt`/constraints-слой + тесты | **M** |
| 4.9 | Нормальное распределение (Box–Muller, μ/σ) | `domain/` + UI | **S** |
| 4.10 | Онлайн-энтропия random.org: сеть, таймауты, офлайн-fallback, строго по запросу | новый `data/EntropyRemote.kt` + настройки | **L** |
| 4.11 | Синхронизация с Google Drive: OAuth, шифрование, только по включению | новый пакет + зависимости | **XL** |
| 4.12 | Автоматизация: Tasker-интенты (запуск + результат), App Actions (голос) | манифест, deep links, new API | **L** |
| 4.13 | Достижения/геймификация (счётчики использования) | `data/`, UI-бейджи | **M** |
| 4.14 | Ежедневное «число дня»: пуш через WorkManager + уведомления | новый воркер + манифест | **M** |
| 4.15 | Блокировка приложения: PIN/биометрия (BiometricPrompt) для скрытых сессий | новый `LockScreen`, настройки | **M** |

---

## Кросс-функциональные задачи (вплетаются в этапы)

| Задача | Этап | Сложность |
|--------|------|-----------|
| Вынос строк в ресурсы, i18n (ru/en) | 1 (1.10) ✅ | **S** |
| Доступность: TalkBack-метки, увеличение шрифта, высокий контраст, озвучка результата | 1, 2 (по ходу) | **S** |
| Анимации: появление числа, «барабан», конфетти при исчерпании | 2 | **M** |
| Крупный режим для презентаций (полноэкранный HUD) | 2–3 | **S** |
| Планшеты/складные: двухпанельный layout (WindowSizeClass) | 3–4 | **M** |

---

## Сводка по этапам

| Этап | Содержание | Оценка |
|------|-----------|--------|
| **1** | Ядро: батч, отмена, копирование/шаринг, пресеты, ред. истории, тема, фидбек, настройки, строки | **✅ выполнен 2026-09-29** (10 задач: 1 XS, 5 S, 4 M; 78 тестов зелёные) |
| **2** | Режимы: абстракция режимов, список, кубики, монета, перемешивание, экспорт | **~6–7 дней** (7 задач: 3 S, 3 M, 1 L) |
| **3** | Сессии и интеграции: миграция истории, теги/архив, импорт-экспорт, статистика/графики, виджет, плитка, ярлыки | **~8–10 дней** (14 задач: 5 S, 5 M, 2 L... вкл. миграцию) |
| **4** | Продвинутое: seed, SecureRandom, веса, доп. режимы, гаусс, сеть, Drive, автоматизация, геймификация | **~10–12 дней** (15 задач, из них 1 XL) |

**Порядок работ:** 1 → 2 → 3 → 4.

**Ключевые зависимости:**
- 3.1 (миграция `GeneratedEntry`) лучше сделать сразу после этапа 1, чтобы не переписывать twice — она нужна 3.9–3.11 и всем режимам этапа 2 с таймстампами.
- 2.0 (абстракция режимов) — фундамент для 2.1–2.4 и 4.4–4.7.
- 4.11 (Drive) можно вынести в отдельный спринт или отложить — не блокирует остальное.
- Каждый этап заверять прогоном `./gradlew test` и обновлением тестов (`NumberGeneratorTest`, `SessionViewModelTest`, `SessionCodecTest`).
