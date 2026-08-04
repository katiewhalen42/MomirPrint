package com.example.momirprint

data class CardFace(
    val name: String,
    val mana_cost: String,
    val type_line: String,
    val oracle_text: String,
    val power: String,
    val toughness: String,
    val loyalty: String,
    val color_indicator: List<String>,
    val image_uris: Map<String, String>
)

data class MagicCard(
    val name: String,
    val layout: String,
    val mana_cost: String,
    val type_line: String,
    val oracle_text: String,
    val power: String,
    val toughness: String,
    val loyalty: String,
    val card_faces: List<CardFace>,
    val color_indicator: List<String>,
    val image_uris: Map<String, String>
)