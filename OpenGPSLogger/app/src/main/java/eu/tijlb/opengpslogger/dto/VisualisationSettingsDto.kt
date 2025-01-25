package eu.tijlb.opengpslogger.dto


data class VisualisationSettingsDto(
    val drawLines: Boolean,
    val lineSize: Float?,
    val dotSize: Float?,
    val connectLinesMaxMinutesDelta: Long
)