package com.example.momirprint

data class CardFace(
    val name: String = "",
    val mana_cost: String = "",
    val type_line: String = "",
    val oracle_text: String = "",
    val power: String = "",
    val toughness: String = "",
    val loyalty: String = "",
    val color_indicator: List<String> = emptyList(),
    val image_uris: Map<String, String> = emptyMap()
)

data class MagicCard(
    val name: String = "",
    val layout: String = "",
    val mana_cost: String = "",
    val type_line: String = "",
    val oracle_text: String = "",
    val power: String = "",
    val toughness: String = "",
    val loyalty: String = "",
    val card_faces: List<CardFace> = emptyList<CardFace>(),
    val color_indicator: List<String> = emptyList(),
    val image_uris: Map<String, String> = emptyMap(),
    val scryfall_uri: String = "",
    val all_parts: List<RelatedCard> = emptyList()
) {
    override fun toString(): String {
        return("Name: $name\n" +
        "Mana Cost: $mana_cost\n" +
        "Type: $type_line\n" +
        "Oracle Text: $oracle_text\n" +
        "Power: $power\n" +
        "Toughness: $toughness\n" +
        "Loyalty: $loyalty\n" +
        "Card Faces: $card_faces\n" +
        "Color Indicator: $color_indicator\n" +
        "Image URIs: $image_uris")
    }

    fun tokenParts(): List<RelatedCard> {
        return all_parts.filter { it.component == "token" }.distinctBy { it.id }
    }
}

data class RelatedCard(
    val id: String = "",
    val component: String = "",
    val name: String = "",
    val type_line: String = "",
    val uri: String = ""
)

fun MagicCard.imageUrl(size: String = "normal"): String? =
    image_uris[size] ?: card_faces.firstOrNull()?.image_uris?.get(size)

fun MagicCard.isFoldableDoubleFaced(): Boolean =
    layout in setOf("transform", "modal_dfc") && card_faces.size >= 2

fun MagicCard.printableFacesInOrder(): List<CardFace> {
    if (!isFoldableDoubleFaced()) return card_faces.take(1)
    // Requested print order: front face first, then the back face.
    return listOf(card_faces[0], card_faces[1])
}

fun MagicCard.printableImageUrlsInOrder(size: String = "normal"): List<String> {
    val faceUrls = printableFacesInOrder().mapNotNull { it.image_uris[size] }
    if (faceUrls.isNotEmpty()) return faceUrls
    return imageUrl(size)?.let(::listOf) ?: emptyList()
}

data class CardSearchResponse(
    val `object`: String = "",
    val total_cards: Int = 0,
    val has_more: Boolean = false,
    val data: List<MagicCard> = emptyList()
)

data class AutocompleteResponse(
    val `object`: String = "",
    val total_cards: Int = 0,
    val data: List<String> = emptyList()
)