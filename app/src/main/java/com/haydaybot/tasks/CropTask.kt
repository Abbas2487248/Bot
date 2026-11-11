package com.haydaybot.tasks

import com.haydaybot.service.InputController
import com.haydaybot.service.VisionEngine
import com.haydaybot.utils.BotConfig
import kotlinx.coroutines.delay

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
        var frameReceived = false
        try {
            // MediaProjection delivers its first frame asynchronously after the
            // virtual display is created. Wait briefly instead of failing instantly.
            var bmp = screen()
            var attempts = 0
            while (bmp == null && attempts < 30) {
                delay(200)
                bmp = screen()
                attempts++
            }

            if (bmp == null) {
                warn("No screenshot frame after 6s — capture service did not deliver a frame")
                return
            }

            frameReceived = true
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
            // If capture has not produced a frame, leave the task due so the
            // scheduler can retry instead of sleeping for the full crop cycle.
            if (frameReceived) markDone()
            log("── Read-only crop diagnosis done; no touches sent ──")
        }
    }
}
