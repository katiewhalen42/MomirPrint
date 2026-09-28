package com.example.momirprint

const val MAX_MANA_VALUE = 16

enum class ColorMatch(val operator: String, val label: String) {
    INCLUDES(">=", "Includes"),
    EXACTLY("=", "Exactly"),
    AT_MOST("<=", "At Most")
}

enum class FormatStatus(val label: String) {
    LEGAL("Legal"),
    NOT_LEGAL("Not Legal"),
    BANNED("Banned"),
    RESTRICTED("Restricted");

    fun clauses(format: String): List<String> = when (this) {
        LEGAL -> listOf("f:$format")
        BANNED -> listOf("banned:$format")
        RESTRICTED -> listOf("restricted:$format")
        // Scryfall has no "not legal" keyword, so it's "none of the other three".
        NOT_LEGAL -> listOf("-f:$format", "-banned:$format", "-restricted:$format")
    }
}

/**
 * Everything the filter sheet can set, and nothing else.
 *
 */
data class CardFilters(
    val types: Set<String> = emptySet(), //Lower-case words, e.g. "creature", "artifact", "planeswalker"
    val colors: Set<Char> = emptySet(), //"WUBRG" characters
    val colorMatch: ColorMatch = ColorMatch.INCLUDES,
    val colorId: Set<Char> = emptySet(), //"WUBRG" characters
    val minManaValue: Int = 0,
    val maxManaValue: Int = MAX_MANA_VALUE,
    val rarities: Set<String> = emptySet(), //Lower-case words, e.g. "common", "uncommon", "rare", "mythic"
    val formats: Map<String, FormatStatus> = emptyMap(),
    val customQuery : String = "" //Raw Scryfall syntax, overrides everything else
)

object ScryfallQueryBuilder {
    //Canonical color order
    private const val COLOR_ORDER = "WUBRG"

    fun build(filters: CardFilters): String {
        //If the custom query is filled, use it above all else
        if (filters.customQuery.isNotBlank()) return filters.customQuery.trim()

        val parts = mutableListOf<String>()

        orGroup("t", filters.types)?.let { parts.add(it) }
        colorClause(filters)?.let { parts.add(it) }
        manaValueClause(filters.minManaValue, filters.maxManaValue)?.let { parts.add(it) }
        orGroup("r", filters.rarities)?.let { parts.add(it) }

        filters.formats.entries
            .sortedBy { it.key }
            .forEach { (format, status) -> parts.addAll(status.clauses(format)) }

        return parts.joinToString(" ")
    }

    fun momir(manaValue: Int): String {
        val parts = mutableListOf("t:creature", "mv=$manaValue")
        return parts.joinToString(" ")
    }

    /** One value -> `t:creature`. Several -> `(t:creature or t:instant)`. None -> null. */
    private fun orGroup(key: String, values: Set<String>): String? = when (values.size) {
        0 -> null
        1 -> "$key:${values.first()}"
        else -> values.sorted().joinToString(" or ", prefix = "(", postfix = ")") { "$key:$it" }
    }

    private fun colorClause(filters: CardFilters): String? {
        if (filters.colors.isEmpty()) return null
        // Colorless can't be combined with real colors, so it wins if it's ever present.
        if ('C' in filters.colors) return "c:c"
        val letters = filters.colors
            .sortedBy { COLOR_ORDER.indexOf(it) }
            .joinToString("")
        return "c${filters.colorMatch.operator}$letters"
    }

    /** Leaves out any bound that is at its default, so an untouched slider adds nothing. */
    private fun manaValueClause(min: Int, max: Int): String? {
        val hasMin = min > 0
        val hasMax = max < MAX_MANA_VALUE
        return when {
            hasMin && hasMax && min == max -> "mv=$min"
            hasMin && hasMax -> "mv>=$min mv<=$max"
            hasMin -> "mv>=$min"
            hasMax -> "mv<=$max"
            else -> null
        }
    }
}