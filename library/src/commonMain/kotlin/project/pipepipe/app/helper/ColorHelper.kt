package project.pipepipe.app.helper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Color utility functions for parsing, validating and manipulating colors.
 */
object ColorHelper {
    private val HEX_COLOR_REGEX = Regex("^#?[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")

    /**
     * Validates and normalizes hex color input.
     *
     * @param raw The raw hex color string (e.g., "#ff0000", "ff0000", "#ff0000ff")
     * @return Normalized hex color string with # prefix and uppercase letters, or null if invalid
     */
    fun sanitizeHexColorInput(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !HEX_COLOR_REGEX.matches(trimmed)) return null
        val normalized = trimmed.removePrefix("#").uppercase()
        return "#$normalized"
    }

    /**
     * Parses a hex color string to a Compose Color object.
     *
     * Supports both #RRGGBB and #AARRGGBB formats.
     *
     * @param hex The hex color string (e.g., "#ff0000", "#80ff0000")
     * @param fallback The fallback color to return if parsing fails (default: Color.White)
     * @return The parsed Color object, or fallback if parsing fails
     */
    fun parseHexColor(hex: String?, fallback: Color = Color.White): Color {
        return try {
            val cleanHex = hex!!.trim().removePrefix("#")
            if (cleanHex.length != 6 && cleanHex.length != 8) {
                return fallback
            }

            val argb = when (cleanHex.length) {
                6 -> 0xFF000000L or cleanHex.toLong(16)
                8 -> cleanHex.toLong(16)
                else -> return fallback
            }.toInt()

            Color(argb)
        } catch (e: Exception) {
            fallback
        }
    }

    /**
     * Parses a hex color string to a Compose Color object, returning null on failure.
     *
     * @param hex The hex color string
     * @return The parsed Color object, or null if parsing fails
     */
    fun parseHexColorOrNull(hex: String): Color? =
        runCatching { parseHexColor(hex, fallback = Color.Transparent) }
            .getOrNull()
            ?.takeIf { it != Color.Transparent }

    /**
     * Determines if a color is light or dark based on its luminance.
     *
     * @param color The color to check
     * @return true if the color is light (luminance > 0.5), false otherwise
     */
    fun isLightColor(color: Color): Boolean {
        return color.luminance() > 0.5f
    }

    /**
     * Gets a contrasting text color (black or white) that will be readable on the given background.
     *
     * @param backgroundColor The background color
     * @return Color.Black with 87% alpha for light backgrounds, Color.White with 95% alpha for dark backgrounds
     */
    fun getContrastingColor(backgroundColor: Color): Color {
        val luminance = backgroundColor.luminance()
        return if (luminance > 0.5f) Color.Black.copy(alpha = 0.87f)
        else Color.White.copy(alpha = 0.95f)
    }
}
