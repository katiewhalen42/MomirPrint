package com.example.momirprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for MagicCard.tokenParts(), which feeds the Tokens tab's dropdown.
 *
 * These are plain JVM tests (they run with `./gradlew test`, no phone or emulator needed)
 * because tokenParts() is an ordinary function on a data class with no Android types in it.
 * That's the payoff of keeping logic out of composables.
 */
class TokenPartsTest {

    private fun part(id: String, component: String, name: String = "Thing") =
        RelatedCard(id = id, component = component, name = name)

    @Test
    fun keeps_only_parts_whose_component_is_token() {
        val card = MagicCard(
            all_parts = listOf(
                part("1", "token", "Soldier"),
                part("2", "combo_piece", "Other Half"),
                part("3", "meld_part", "Meld Partner"),
                part("4", "token", "Treasure")
            )
        )

        assertEquals(listOf("Soldier", "Treasure"), card.tokenParts().map { it.name })
    }

    @Test
    fun removes_duplicate_entries_for_the_same_token() {
        val card = MagicCard(
            all_parts = listOf(
                part("1", "token", "Soldier"),
                part("1", "token", "Soldier"),
                part("2", "token", "Spirit")
            )
        )

        assertEquals(listOf("Soldier", "Spirit"), card.tokenParts().map { it.name })
    }

    @Test
    fun is_empty_when_the_card_lists_no_related_parts() {
        // Scryfall leaves all_parts out entirely for most cards; the model's default is an empty list.
        assertTrue(MagicCard(name = "Plain Card").tokenParts().isEmpty())
    }

    @Test
    fun is_empty_when_the_only_related_parts_are_not_tokens() {
        val card = MagicCard(all_parts = listOf(part("1", "combo_piece")))

        assertTrue(card.tokenParts().isEmpty())
    }
}
