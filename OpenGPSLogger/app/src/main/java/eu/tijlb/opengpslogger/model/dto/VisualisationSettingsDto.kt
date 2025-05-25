package eu.tijlb.opengpslogger.model.dto

import eu.tijlb.opengpslogger.model.database.settings.ColorMode


data class VisualisationSettingsDto(
    val drawLines: Boolean,
    val drawDensityMap: Boolean,
    val lineSize: Float?,
    val dotSize: Float?,
    val connectLinesMaxMinutesDelta: Long,
    val colorMode: ColorMode,
    val colorSeed: Int,
    val opacityPercentage: Int
)