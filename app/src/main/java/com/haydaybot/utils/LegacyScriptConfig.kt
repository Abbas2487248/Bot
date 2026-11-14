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
        require(totalFarms >= 1) { "totalFarms must be at least 1" }
        require(layout >= 1) { "layout must be a positive layout id" }
        require(field >= 1) { "field must be a positive field id" }
        require(trays >= 1) { "trays must be a positive tray id" }
        require(price >= 1) { "price must be a positive price preset id" }
        require(squirrel in 0..1) { "squirrel must be 0 or 1" }
        require(beehiveTree in 0..1) { "beehiveTree must be 0 or 1" }
        require(honeyExtractor in 0..1) { "honeyExtractor must be 0 or 1" }
    }

    val usesCloneFarm: Boolean get() = runMethod == 2
    val squirrelEnabled: Boolean get() = squirrel == 1
    val beehiveTreeEnabled: Boolean get() = beehiveTree == 1
    val honeyExtractorEnabled: Boolean get() = honeyExtractor == 1

    /** Field 1 is the wheat field in the supplied original script. */
    val selectedCropName: String? get() = when (field) {
        1 -> "wheat"
        else -> null // Other legacy field IDs are mapped when their script section is ported.
    }
}
