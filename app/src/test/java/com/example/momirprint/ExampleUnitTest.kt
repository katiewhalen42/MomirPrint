package com.example.momirprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun foldable_double_faced_layouts_are_transform_and_modal_only() {
        val transform = MagicCard(layout = "transform", card_faces = listOf(CardFace(), CardFace()))
        val modal = MagicCard(layout = "modal_dfc", card_faces = listOf(CardFace(), CardFace()))
        val reversible = MagicCard(layout = "reversible_card", card_faces = listOf(CardFace(), CardFace()))

        assertTrue(transform.isFoldableDoubleFaced())
        assertTrue(modal.isFoldableDoubleFaced())
        assertFalse(reversible.isFoldableDoubleFaced())
    }

    @Test
    fun printable_faces_for_fold_print_are_front_then_back() {
        val front = CardFace(name = "Front")
        val back = CardFace(name = "Back")
        val card = MagicCard(layout = "transform", card_faces = listOf(front, back))

        val ordered = card.printableFacesInOrder().map { it.name }

        assertEquals(listOf("Front", "Back"), ordered)
    }

    @Test
    fun printable_image_urls_fallback_to_single_image_uri() {
        val card = MagicCard(
            layout = "normal",
            image_uris = mapOf("normal" to "https://img/front.jpg")
        )

        assertEquals(listOf("https://img/front.jpg"), card.printableImageUrlsInOrder())
    }
}