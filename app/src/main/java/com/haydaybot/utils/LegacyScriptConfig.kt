package com.haydaybot.utils

/**
 * Preserved settings from the user's original HiroMacro farm script.
 *
 * Values intentionally retain the original numeric meanings so the
 * Kotlin task engine can migrate each script section without silently
 * changing the user's setup.
 */
data class LegacyScriptConfig(
    val runMethod: Int = 2,       // 1 = HD Manager Mega, 2 = CloneFarm
    val totalFarms: Int = 1,
    val layout: Int = 2,
    val field: Int = 1,
    val trays: Int = 1,
    val price: Int = 1,
    val squirrel: Int = 0,
    val beehiveTree: Int = 0,
    val honeyExtractor: Int = 0,
) {
    init {
        require(runMethod in 1..2) { "runMethod must be 1 or 2" }
        require(totalFarms in 1..11) { "totalFarms must be between 1 and 11" }
        require(layout in 1..2) { "layout must be 1 (classic) or 2 (new)" }
        require(field in 1..5) { "field must be between 1 and 5" }
        require(trays in 0..3) { "trays must be between 0 and 3" }
        require(price in 0..2) { "price must be between 0 and 2" }
        require(squirrel in 0..1) { "squirrel must be 0 or 1" }
        require(beehiveTree in 0..2) { "beehiveTree must be 0, 1, or 2" }
        require(honeyExtractor in 0..2) { "honeyExtractor must be 0, 1, or 2" }
    }

    val usesCloneFarm: Boolean get() = runMethod == 2
    val squirrelEnabled: Boolean get() = squirrel == 1
    val beehiveTreeEnabled: Boolean get() = beehiveTree == 1
    val honeyExtractorEnabled: Boolean get() = honeyExtractor == 1

    /** Field 1 is the wheat field in the supplied original script. */
    val selectedCropName: String get() = when (field) {
        1 -> "wheat"
        2 -> "corn"
        3 -> "carrot"
        4 -> "soybean"
        5 -> "sugarcane"
        else -> error("Unsupported field: $field")
    }
}
