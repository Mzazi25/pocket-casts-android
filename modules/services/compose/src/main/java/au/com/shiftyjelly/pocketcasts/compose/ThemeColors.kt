package au.com.shiftyjelly.pocketcasts.compose

import androidx.compose.ui.graphics.Color
import au.com.shiftyjelly.pocketcasts.compose.folder.FolderColor

data class ThemeColors(
    val primaryUi01: Color,
    val primaryUi01Active: Color,
    val primaryUi02: Color,
    val primaryUi02Selected: Color,
    val primaryUi02Active: Color,
    val primaryUi03: Color,
    val primaryUi04: Color,
    val primaryUi05: Color,
    val primaryUi05Selected: Color,
    val primaryUi06: Color,
    val primaryIcon01: Color,
    val primaryIcon01Active: Color,
    val primaryIcon02: Color,
    val primaryIcon02Selected: Color,
    val primaryIcon02Active: Color,
    val primaryIcon03Active: Color,
    val primaryIcon03: Color,
    val primaryText01: Color,
    val primaryText02: Color,
    val primaryText02Selected: Color,
    val primaryField01: Color,
    val primaryField01Active: Color,
    val primaryField02: Color,
    val primaryField02Active: Color,
    val primaryField03: Color,
    val primaryField03Active: Color,
    val primaryInteractive01: Color,
    val primaryInteractive01Hover: Color,
    val primaryInteractive01Active: Color,
    val primaryInteractive01Disabled: Color,
    val primaryInteractive02: Color,
    val primaryInteractive02Hover: Color,
    val primaryInteractive02Active: Color,
    val primaryInteractive03: Color,
    val secondaryUi01: Color,
    val secondaryUi02: Color,
    val secondaryIcon01: Color,
    val secondaryIcon02: Color,
    val secondaryText01: Color,
    val secondaryText02: Color,
    val secondaryField01: Color,
    val secondaryField01Active: Color,
    val secondaryInteractive01: Color,
    val secondaryInteractive01Hover: Color,
    val secondaryInteractive01Active: Color,
    val support01: Color,
    val support02: Color,
    val support03: Color,
    val support04: Color,
    val support05: Color,
    val support06: Color,
    val support07: Color,
    val support08: Color,
    val support09: Color,
    val support10: Color,
    val playerContrast01: Color,
    val playerContrast02: Color,
    val playerContrast03: Color,
    val playerContrast04: Color,
    val playerContrast05: Color,
    val playerContrast06: Color,
    val contrast01: Color,
    val contrast02: Color,
    val contrast03: Color,
    val contrast04: Color,
    val filter01: Color,
    val filter02: Color,
    val filter03: Color,
    val filter04: Color,
    val filter05: Color,
    val filter06: Color,
    val filter07: Color,
    val filter08: Color,
    val filter09: Color,
    val filter10: Color,
    val filter11: Color,
    val filter12: Color,
    val veil: Color,
    val gradient01A: Color,
    val gradient01E: Color,
    val gradient02A: Color,
    val gradient02E: Color,
    val gradient03A: Color,
    val gradient03E: Color,
    val gradient04A: Color,
    val gradient04E: Color,
    val gradient05A: Color,
    val gradient05E: Color,
    val category01: Color,
    val category02: Color,
    val category03: Color,
    val category04: Color,
    val category05: Color,
    val category06: Color,
    val category07: Color,
    val category08: Color,
    val category09: Color,
    val category10: Color,
    val category11: Color,
    val category12: Color,
    val category13: Color,
    val category14: Color,
    val category15: Color,
    val category16: Color,
    val category17: Color,
    val category18: Color,
    val category19: Color
) {

    val folderColors = listOf(
        FolderColor(id = 0, color = filter01),
        FolderColor(id = 6, color = filter07),
        FolderColor(id = 2, color = filter03),
        FolderColor(id = 1, color = filter02),
        FolderColor(id = 3, color = filter04),
        FolderColor(id = 9, color = filter10),
        FolderColor(id = 7, color = filter08),
        FolderColor(id = 4, color = filter05),
        FolderColor(id = 10, color = filter11),
        FolderColor(id = 8, color = filter09),
        FolderColor(id = 5, color = filter06),
        FolderColor(id = 11, color = filter12)
    )

    private val folderIdToColor = folderColors.associateBy({ it.id }, { it.color })

    fun getFolderColor(id: Int): Color = folderIdToColor[id] ?: folderColors.first().color
}