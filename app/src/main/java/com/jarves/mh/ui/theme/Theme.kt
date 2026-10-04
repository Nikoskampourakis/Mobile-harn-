package com.jarves.mh.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val PocketOrange = Color(0xFFF28C52)
val PocketBlue = Color(0xFF8EA8FF)
val PocketGreen = Color(0xFF69D69E)
val PocketBackground = Color(0xFF0B0E14)
val PocketSurface = Color(0xFF131821)
val PocketSurfaceVariant = Color(0xFF1B222D)
val PocketOutline = Color(0xFF2A3240)

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

enum class AppColorTheme(
    val id: String,
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val description: String,
) {
    AMBER(
        id = "amber",
        displayName = "Flame Amber",
        primaryColor = Color(0xFFF28C52),
        secondaryColor = Color(0xFF8EA8FF),
        description = "Classic warm developer orange",
    ),
    SAPPHIRE(
        id = "sapphire",
        displayName = "Cyber Sapphire",
        primaryColor = Color(0xFF3B82F6),
        secondaryColor = Color(0xFF38BDF8),
        description = "Vibrant electric cobalt & cyan",
    ),
    EMERALD(
        id = "emerald",
        displayName = "Matrix Emerald",
        primaryColor = Color(0xFF10B981),
        secondaryColor = Color(0xFF34D399),
        description = "Modern terminal mint & forest green",
    ),
    AMETHYST(
        id = "amethyst",
        displayName = "Neon Amethyst",
        primaryColor = Color(0xFFA855F7),
        secondaryColor = Color(0xFFE879F9),
        description = "Futuristic cyber violet & magenta",
    ),
    CORAL(
        id = "coral",
        displayName = "Sunset Coral",
        primaryColor = Color(0xFFFB7185),
        secondaryColor = Color(0xFFFBBF24),
        description = "Warm coral rose & sunset gold",
    ),
    CRIMSON(
        id = "crimson",
        displayName = "Ruby Crimson",
        primaryColor = Color(0xFFF43F5E),
        secondaryColor = Color(0xFFFB923C),
        description = "Bold high-energy crimson ruby",
    ),
    SLATE(
        id = "slate",
        displayName = "Titanium Slate",
        primaryColor = Color(0xFF94A3B8),
        secondaryColor = Color(0xFF38BDF8),
        description = "Minimal sleek monochromatic steel",
    );

    companion object {
        fun fromId(id: String?): AppColorTheme =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AMBER
    }
}

private fun createDarkColorScheme(theme: AppColorTheme): ColorScheme = when (theme) {
    AppColorTheme.AMBER -> darkColorScheme(
        primary = PocketOrange,
        onPrimary = Color(0xFF241107),
        primaryContainer = Color(0xFF42281D),
        onPrimaryContainer = Color(0xFFFFDDCC),
        secondary = PocketBlue,
        onSecondary = Color(0xFF001F58),
        tertiary = PocketGreen,
        onTertiary = Color(0xFF00391E),
        background = PocketBackground,
        onBackground = Color(0xFFE6EDF3),
        surface = PocketSurface,
        onSurface = Color(0xFFE6EDF3),
        surfaceVariant = PocketSurfaceVariant,
        onSurfaceVariant = Color(0xFF9AA0A6),
        outline = PocketOutline,
        outlineVariant = Color(0xFF333B4A),
    )
    AppColorTheme.SAPPHIRE -> darkColorScheme(
        primary = Color(0xFF60A5FA),
        onPrimary = Color(0xFF001D4D),
        primaryContainer = Color(0xFF1E3A8A),
        onPrimaryContainer = Color(0xFFDBEAFE),
        secondary = Color(0xFF38BDF8),
        onSecondary = Color(0xFF082F49),
        tertiary = Color(0xFF6EE7B7),
        onTertiary = Color(0xFF022C22),
        background = Color(0xFF080D1A),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF0F172A),
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF334155),
        outlineVariant = Color(0xFF1E293B),
    )
    AppColorTheme.EMERALD -> darkColorScheme(
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF022C22),
        primaryContainer = Color(0xFF064E3B),
        onPrimaryContainer = Color(0xFFD1FAE5),
        secondary = Color(0xFF38BDF8),
        onSecondary = Color(0xFF082F49),
        tertiary = Color(0xFFA7F3D0),
        onTertiary = Color(0xFF064E3B),
        background = Color(0xFF061412),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF0B211E),
        onSurface = Color(0xFFE6F4EA),
        surfaceVariant = Color(0xFF132F2A),
        onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF1D4A41),
        outlineVariant = Color(0xFF132F2A),
    )
    AppColorTheme.AMETHYST -> darkColorScheme(
        primary = Color(0xFFC084FC),
        onPrimary = Color(0xFF3B0764),
        primaryContainer = Color(0xFF581C87),
        onPrimaryContainer = Color(0xFFF3E8FF),
        secondary = Color(0xFFF472B6),
        onSecondary = Color(0xFF500724),
        tertiary = Color(0xFF818CF8),
        onTertiary = Color(0xFF1E1B4B),
        background = Color(0xFF0F0817),
        onBackground = Color(0xFFF1E8FA),
        surface = Color(0xFF180E24),
        onSurface = Color(0xFFF3E8FF),
        surfaceVariant = Color(0xFF261838),
        onSurfaceVariant = Color(0xFFA89BB5),
        outline = Color(0xFF3D2A54),
        outlineVariant = Color(0xFF261838),
    )
    AppColorTheme.CORAL -> darkColorScheme(
        primary = Color(0xFFFDA4AF),
        onPrimary = Color(0xFF4C0519),
        primaryContainer = Color(0xFF881337),
        onPrimaryContainer = Color(0xFFFFE4E6),
        secondary = Color(0xFFFBBF24),
        onSecondary = Color(0xFF451A03),
        tertiary = Color(0xFFFB923C),
        onTertiary = Color(0xFF431407),
        background = Color(0xFF140A0D),
        onBackground = Color(0xFFFCE7F3),
        surface = Color(0xFF201017),
        onSurface = Color(0xFFFFF1F2),
        surfaceVariant = Color(0xFF301A24),
        onSurfaceVariant = Color(0xFFA8949D),
        outline = Color(0xFF4A2837),
        outlineVariant = Color(0xFF301A24),
    )
    AppColorTheme.CRIMSON -> darkColorScheme(
        primary = Color(0xFFFB7185),
        onPrimary = Color(0xFF4C0519),
        primaryContainer = Color(0xFF9F1239),
        onPrimaryContainer = Color(0xFFFFE4E6),
        secondary = Color(0xFFF97316),
        onSecondary = Color(0xFF431407),
        tertiary = Color(0xFFF43F5E),
        onTertiary = Color(0xFF4C0519),
        background = Color(0xFF12080A),
        onBackground = Color(0xFFFCE7F3),
        surface = Color(0xFF1E0D11),
        onSurface = Color(0xFFFFF1F2),
        surfaceVariant = Color(0xFF2E151C),
        onSurfaceVariant = Color(0xFFA69096),
        outline = Color(0xFF48202B),
        outlineVariant = Color(0xFF2E151C),
    )
    AppColorTheme.SLATE -> darkColorScheme(
        primary = Color(0xFFCBD5E1),
        onPrimary = Color(0xFF0F172A),
        primaryContainer = Color(0xFF334155),
        onPrimaryContainer = Color(0xFFF1F5F9),
        secondary = Color(0xFF94A3B8),
        onSecondary = Color(0xFF0F172A),
        tertiary = Color(0xFF38BDF8),
        onTertiary = Color(0xFF082F49),
        background = Color(0xFF090D14),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF111827),
        onSurface = Color(0xFFF8FAFC),
        surfaceVariant = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF334155),
        outlineVariant = Color(0xFF1E293B),
    )
}

private fun createLightColorScheme(theme: AppColorTheme): ColorScheme = when (theme) {
    AppColorTheme.AMBER -> lightColorScheme(
        primary = Color(0xFFD85A20),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE0D2),
        onPrimaryContainer = Color(0xFF451A08),
        secondary = Color(0xFF3366CC),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF1B8A5A),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF1F2328),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1F2328),
        surfaceVariant = Color(0xFFEAEFF5),
        onSurfaceVariant = Color(0xFF57606A),
        outline = Color(0xFFD0D7DE),
        outlineVariant = Color(0xFFD8DEE4),
    )
    AppColorTheme.SAPPHIRE -> lightColorScheme(
        primary = Color(0xFF2563EB),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDBEAFE),
        onPrimaryContainer = Color(0xFF1E3A8A),
        secondary = Color(0xFF0284C7),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF059669),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1),
        outlineVariant = Color(0xFFE2E8F0),
    )
    AppColorTheme.EMERALD -> lightColorScheme(
        primary = Color(0xFF059669),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD1FAE5),
        onPrimaryContainer = Color(0xFF064E3B),
        secondary = Color(0xFF0D9488),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF0284C7),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFF7FDF9),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE1F4EC),
        onSurfaceVariant = Color(0xFF335C4E),
        outline = Color(0xFFB8E2D2),
        outlineVariant = Color(0xFFDCF0E7),
    )
    AppColorTheme.AMETHYST -> lightColorScheme(
        primary = Color(0xFF9333EA),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF3E8FF),
        onPrimaryContainer = Color(0xFF581C87),
        secondary = Color(0xFFDB2777),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF4F46E5),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFFAF7FD),
        onBackground = Color(0xFF1E102F),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1E102F),
        surfaceVariant = Color(0xFFEFE8F7),
        onSurfaceVariant = Color(0xFF554468),
        outline = Color(0xFFD5C4E6),
        outlineVariant = Color(0xFFECE4F5),
    )
    AppColorTheme.CORAL -> lightColorScheme(
        primary = Color(0xFFE11D48),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE4E6),
        onPrimaryContainer = Color(0xFF881337),
        secondary = Color(0xFFD97706),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFFEA580C),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFFFF7F8),
        onBackground = Color(0xFF2C1016),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF2C1016),
        surfaceVariant = Color(0xFFF7E7EB),
        onSurfaceVariant = Color(0xFF67434D),
        outline = Color(0xFFE6C4CC),
        outlineVariant = Color(0xFFF5E4E8),
    )
    AppColorTheme.CRIMSON -> lightColorScheme(
        primary = Color(0xFFDC2626),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFEE2E2),
        onPrimaryContainer = Color(0xFF7F1D1D),
        secondary = Color(0xFFEA580C),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFFE11D48),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFFFF7F7),
        onBackground = Color(0xFF2E1010),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF2E1010),
        surfaceVariant = Color(0xFFF7E5E5),
        onSurfaceVariant = Color(0xFF6B4545),
        outline = Color(0xFFE6C2C2),
        outlineVariant = Color(0xFFF5E2E2),
    )
    AppColorTheme.SLATE -> lightColorScheme(
        primary = Color(0xFF475569),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE2E8F0),
        onPrimaryContainer = Color(0xFF0F172A),
        secondary = Color(0xFF64748B),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF0284C7),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1),
        outlineVariant = Color(0xFFE2E8F0),
    )
}

@Composable
fun PocketTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorTheme: AppColorTheme = AppColorTheme.AMBER,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    val scheme = if (isDark) createDarkColorScheme(colorTheme) else createLightColorScheme(colorTheme)

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
