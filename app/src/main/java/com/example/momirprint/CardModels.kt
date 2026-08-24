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
    val scryfall_uri: String = ""
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
}

data class CardSearchResponse(
    val `object`: String = "",
    val total_cards: Int = 0,
    val has_more: Boolean = false,
    val data: List<MagicCard> = emptyList()
)

