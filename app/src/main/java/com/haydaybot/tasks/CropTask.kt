package com.haydaybot.tasks

import com.haydaybot.service.InputController
import com.haydaybot.service.VisionEngine
import com.haydaybot.utils.BotConfig

/**
 * Diagnostic-only crop task. It deliberately performs no taps or swipes.
 */
class CropTask(
    input: InputController,
    vision: VisionEngine,
    config: BotConfig,
) : BaseTask(input, vision, config) {

    override val displayName = "Crop diagnostics"
    override val cycleMs = config.cropCycleMs

    override suspend fun run() {
        log("── Read-only crop diagnosis start ──")
        try {
            val bmp = screen()
            if (bmp == null) {
                warn("Screenshot unavailable — check screen-capture permission/service")
                return
            }
            log("Screenshot: ${bmp.width}x${bmp.height}")
            listOf(
                "harvest_ready",
                "harvest_btn",
                "plot_empty",
                "seed_wheat"
            ).forEach { template ->
                log(vision.diagnose(bmp, "crops", template))
            }
        } catch (e: Exception) {
            err("Crop diagnosis error", e)
        } finally {
            markDone()
            log("── Read-only crop diagnosis done; no touches sent ──")
        }
    }
}
