package CodeSyS.Random_Number.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Параметры режима генерации внутри сессии.
 *
 * Режимы делят одну модель [Session] (журнал, время, seed) и отличаются
 * тем, *что* и *как* генерируется. Это позволяет держать все сессии в одном
 * хранилище и одном списке на Home, не плодя отдельные таблицы и экраны.
 *
 * В JSON сессии payload пишется в поле `payload` с дискриминатором `type`
 * (`Json { classDiscriminator = "type" }`): новый режим добавляется новым
 * подтипом с `@SerialName`, а сессии, записанные до появления дискриминатора,
 * читаются как [Numbers] — см. [SessionCodec.normalizeSessionJson].
 *
 * Журнал ([Session.log]) хранит для [Numbers] сами числа, а для [Items] —
 * индексы элементов в списке (отображение — [Session.displayOf]).
 */
@Serializable
sealed interface SessionPayload {

    /**
     * Генерация чисел в диапазоне `[min, max]` (включительно).
     *
     * @property min нижняя граница диапазона.
     * @property max верхняя граница диапазона.
     * @property allowRepeats `true` — повторы разрешены; `false` — уже
     * выданные числа не появляются снова.
     */
    @Serializable
    @SerialName("numbers")
    data class Numbers(
        val min: Int,
        val max: Int,
        val allowRepeats: Boolean,
    ) : SessionPayload {

        init {
            require(min <= max) { "Некорректный диапазон: min=$min > max=$max" }
        }

        /**
         * Размер диапазона в [Long]: для `Int.MIN_VALUE..Int.MAX_VALUE`
         * он не помещается в [Int].
         */
        val size: Long get() = max.toLong() - min.toLong() + 1L
    }

    /**
     * Выбор случайного элемента из пользовательского списка (задача 2.1).
     *
     * @property items исходный список элементов в порядке ввода
     * (пустые строки и дубликаты удаляются при создании). Список не пуст.
     * @property allowRepeats `true` — элемент может выпасть повторно;
     * `false` — выпавшие элементы не появляются снова.
     */
    @Serializable
    @SerialName("items")
    data class Items(
        val items: List<String>,
        val allowRepeats: Boolean,
    ) : SessionPayload {

        init {
            require(items.isNotEmpty()) { "Список элементов пуст" }
        }
    }

    /**
     * Бросок набора кубиков (задача 2.2).
     *
     * Один бросок даёт [count] значений по [sides] граней, сумма —
     * результат броска. Кубики кидаются повторно (повторы допустимы),
     * поэтому исчерпание для режима невозможно.
     *
     * @property count число кубиков в броске (≥ 1).
     * @property sides число граней кубика (≥ 2).
     */
    @Serializable
    @SerialName("dice")
    data class Dice(
        val count: Int,
        val sides: Int,
    ) : SessionPayload {

        init {
            require(count >= 1) { "Некорректное количество кубиков: count=$count" }
            require(sides >= 2) { "У кубика минимум две грани: sides=$sides" }
        }

        /** Обозначение броска, например «2d6». */
        val label: String get() = "${count}d$sides"
    }

    /**
     * Бросок монеты («орёл/решка», задача 2.3).
     *
     * Журнал хранит стороны как значения `CoinSide.ordinal` (0 — орёл,
     * 1 — решка), поэтому история, экспорт и вывод работают общими
     * механизмами. Броски всегда повторяемы — исчерпание невозможно.
     */
    @Serializable
    @SerialName("coin")
    data object Coin : SessionPayload

    /**
     * Перемешивание списка (задача 2.4).
     *
     * Одно нажатие даёт новую случайную перестановку всех элементов
     * (тасование Фишера–Йетса). Журнал хранит индексы элементов в порядке
     * перемешивания — как у [Items], поэтому история, экспорт и вывод
     * работают общими механизмами. Перестановок можно запросить сколько
     * угодно — исчерпание невозможно.
     *
     * @property items исходный список элементов в порядке ввода
     * (пустые строки и дубликаты удаляются при создании). Список не пуст.
     */
    @Serializable
    @SerialName("shuffle")
    data class Shuffle(
        val items: List<String>,
    ) : SessionPayload {

        init {
            require(items.isNotEmpty()) { "Список для перемешивания пуст" }
        }
    }
}
