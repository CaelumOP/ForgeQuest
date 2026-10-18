package com.forgequest.ai.ui

import com.forgequest.ai.domain.model.AiModelTier
import com.forgequest.ai.domain.model.FlameHealthState
import com.forgequest.ai.domain.model.HiddenFatAddition
import com.forgequest.ai.domain.model.HunterRank
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.PortionMultiplier
import com.forgequest.ai.ui.components.ANATOMICAL_MUSCLE_REGION_PATHS
import com.forgequest.ai.ui.components.BezierCommand
import com.forgequest.ai.ui.components.muscleRecoveryColor
import com.forgequest.ai.ui.components.stripFencedJsonBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

class VisualScreenshotRendererTest {

    private val sampleHeatMap = mapOf(
        MuscleGroup.CHEST to 0.92f,
        MuscleGroup.FRONT_DELTS to 0.78f,
        MuscleGroup.SIDE_DELTS to 0.55f,
        MuscleGroup.REAR_DELTS to 0.48f,
        MuscleGroup.TRICEPS to 0.85f,
        MuscleGroup.BICEPS to 0.75f,
        MuscleGroup.FOREARMS to 0.45f,
        MuscleGroup.LATS to 0.28f,
        MuscleGroup.UPPER_BACK_TRAPS to 0.45f,
        MuscleGroup.LOWER_BACK to 0.35f,
        MuscleGroup.ABS_CORE to 0.55f,
        MuscleGroup.OBLIQUES to 0.25f,
        MuscleGroup.QUADS to 0.14f,
        MuscleGroup.HAMSTRINGS to 0.88f,
        MuscleGroup.GLUTES to 0.65f,
        MuscleGroup.CALVES to 0.18f
    )

    @Test
    fun exportAnatomicalBezierPathsAndVerifyDesignTokens() {
        val regions = ANATOMICAL_MUSCLE_REGION_PATHS
        assertTrue("Expected at least 30 anatomical muscle/silhouette regions", regions.size >= 30)
        assertTrue("Expected front regions", regions.any { it.isFront && it.group == MuscleGroup.CHEST })
        assertTrue("Expected back regions", regions.any { !it.isFront && it.group == MuscleGroup.LATS })

        // Verify DomainModel labels match Google Fit Sentence-case specification
        assertEquals("Fast", AiModelTier.GEMINI_3_8_FLASH.displayName)
        assertEquals("Deep", AiModelTier.GEMINI_4_ARGON.displayName)
        assertEquals("Starter Athlete", HunterRank.E_RANK.title)
        assertEquals("Inferno · 100% health", FlameHealthState.INFERNO.label)
        assertEquals("Small 0.75x", PortionMultiplier.SMALL.label)
        assertEquals("Heavy (+150)", HiddenFatAddition.HEAVY.label)

        // Verify Markdown fenced JSON stripper
        val sampleMd = "### Plan\n**Bold**\n```json\n{\"planName\":\"Test\"}\n```"
        val stripped = stripFencedJsonBlocks(sampleMd)
        assertTrue(!stripped.contains("```json"))

        val outDir = File("screenshots").apply { mkdirs() }
        val tsvFile = File(outDir, "anatomical_regions.tsv")
        val sb = StringBuilder()

        for (region in regions) {
            val cmds = region.traceCommands(1f, 1f)
            assertTrue("Region ${region.label} must have Bezier commands", cmds.isNotEmpty())
            val intensity = if (region.group != null) (sampleHeatMap[region.group] ?: 0f) else -1f
            val color = if (region.group != null) {
                val c = muscleRecoveryColor(intensity)
                val r = (c.red * 255f).roundToInt().coerceIn(0, 255)
                val g = (c.green * 255f).roundToInt().coerceIn(0, 255)
                val b = (c.blue * 255f).roundToInt().coerceIn(0, 255)
                String.format("#%02X%02X%02X", r, g, b)
            } else {
                "#242628"
            }

            val serializedCmds = cmds.joinToString(";") { cmd ->
                when (cmd) {
                    is BezierCommand.MoveTo -> "M,${cmd.x},${cmd.y}"
                    is BezierCommand.LineTo -> "L,${cmd.x},${cmd.y}"
                    is BezierCommand.QuadTo -> "Q,${cmd.cx},${cmd.cy},${cmd.ex},${cmd.ey}"
                    is BezierCommand.CubicTo -> "C,${cmd.c1x},${cmd.c1y},${cmd.c2x},${cmd.c2y},${cmd.ex},${cmd.ey}"
                    BezierCommand.Close -> "Z"
                }
            }
            sb.append("${region.isFront}\t${region.group?.name ?: "SILHOUETTE"}\t${region.label}\t$color\t$intensity\t$serializedCmds\n")
        }

        tsvFile.writeText(sb.toString())
        assertTrue(tsvFile.exists() && tsvFile.length() > 500)
    }
}
