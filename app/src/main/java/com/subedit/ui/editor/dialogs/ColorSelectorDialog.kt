package com.subedit.ui.editor.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.subedit.core.domain.AssColorConverter
import com.subedit.ui.theme.AccentCyan
import com.subedit.ui.theme.BgDarkest
import com.subedit.ui.theme.BgModalHeader
import com.subedit.ui.theme.BgPanel
import com.subedit.ui.theme.BgSurface
import com.subedit.ui.theme.BorderColor
import com.subedit.ui.theme.BorderHighlight
import com.subedit.ui.theme.TextMain
import com.subedit.ui.theme.TextMuted
import kotlin.math.roundToInt

/**
 * Floating Modal Dialog **"Color Selector"** untuk tombol chip tag warna (`\c`, `\2c`, `\3c`, `\4c`).
 *
 * Fitur:
 * - Kotak pratinjau warna aktif (Active Color Preview Box) dengan latar checkerboard transparan.
 * - Pemilih warna visual (Swatches + RGB Sliders).
 * - 3 Kolom Kode Warna Tersinkronisasi:
 *   1. Format ASS: `&HBBGGRR&` (Blue-Green-Red).
 *   2. Format Hex/HTML: `#RRGGBB`.
 *   3. Format Alpha (Transparansi): Hex `&HAA&` dan slider nilai `0 - 255`.
 * - Tombol `Batal` dan `Terapkan Warna`: menyisipkan tag warna (`{\c&HBBGGRR&}`, `{\2c&HBBGGRR&}`,
 *   `{\3c&HBBGGRR&}`, atau `{\4c&HBBGGRR&}` beserta tag alpha jika transparansi > 0).
 */
@Composable
fun ColorSelectorDialog(
    tagType: String, // "\\c", "\\2c", "\\3c", atau "\\4c"
    initialHexColor: String = "#38BDF8",
    onDismiss: () -> Unit,
    onApplyColorTag: (formattedAssTag: String) -> Unit,
) {
    val cleanInitial = remember(initialHexColor) {
        val c = initialHexColor.trim().removePrefix("#")
        if (c.length == 6) "#${c.uppercase()}" else "#38BDF8"
    }

    var rChannel by remember(cleanInitial) {
        mutableIntStateOf(cleanInitial.substring(1, 3).toIntOrNull(16) ?: 0x38)
    }
    var gChannel by remember(cleanInitial) {
        mutableIntStateOf(cleanInitial.substring(3, 5).toIntOrNull(16) ?: 0xBD)
    }
    var bChannel by remember(cleanInitial) {
        mutableIntStateOf(cleanInitial.substring(5, 7).toIntOrNull(16) ?: 0xF8)
    }
    var alphaInt by remember { mutableIntStateOf(0) } // 0 = Opaque (&H00&), 255 = Transparent (&HFF&)

    var hexInputText by remember(cleanInitial) { mutableStateOf(cleanInitial) }
    var assBgrInputText by remember(cleanInitial) {
        mutableStateOf(AssColorConverter.hexToAssBgrTag(cleanInitial))
    }
    var alphaHexInputText by remember { mutableStateOf("&H00&") }

    fun syncTextsFromRgb(r: Int, g: Int, b: Int) {
        val rc = r.coerceIn(0, 255)
        val gc = g.coerceIn(0, 255)
        val bc = b.coerceIn(0, 255)
        rChannel = rc
        gChannel = gc
        bChannel = bc
        val hex = String.format("#%02X%02X%02X", rc, gc, bc)
        hexInputText = hex
        assBgrInputText = AssColorConverter.hexToAssBgrTag(hex)
    }

    fun syncFromHexString(rawHex: String) {
        hexInputText = rawHex.uppercase()
        val cleaned = rawHex.trim().removePrefix("#")
        if (cleaned.length == 6 && cleaned.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) {
            val r = cleaned.substring(0, 2).toInt(16)
            val g = cleaned.substring(2, 4).toInt(16)
            val b = cleaned.substring(4, 6).toInt(16)
            rChannel = r
            gChannel = g
            bChannel = b
            assBgrInputText = AssColorConverter.hexToAssBgrTag("#${cleaned.uppercase()}")
        }
    }

    fun syncFromAssBgrString(rawAss: String) {
        assBgrInputText = rawAss.uppercase()
        val cleaned = rawAss.trim()
            .removePrefix("&H").removePrefix("&h")
            .removeSuffix("&")
        if (cleaned.length == 6 && cleaned.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) {
            val bb = cleaned.substring(0, 2).toInt(16)
            val gg = cleaned.substring(2, 4).toInt(16)
            val rr = cleaned.substring(4, 6).toInt(16)
            rChannel = rr
            gChannel = gg
            bChannel = bb
            hexInputText = String.format("#%02X%02X%02X", rr, gg, bb)
        }
    }

    fun syncAlphaFromInt(newAlpha: Int) {
        val clamped = newAlpha.coerceIn(0, 255)
        alphaInt = clamped
        alphaHexInputText = String.format("&H%02X&", clamped)
    }

    fun syncAlphaFromHexString(rawAlpha: String) {
        alphaHexInputText = rawAlpha.uppercase()
        val cleaned = rawAlpha.trim()
            .removePrefix("&H").removePrefix("&h")
            .removeSuffix("&")
        if (cleaned.length in 1..2 && cleaned.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) {
            alphaInt = cleaned.toInt(16).coerceIn(0, 255)
        }
    }

    val currentHex = String.format("#%02X%02X%02X", rChannel, gChannel, bChannel)
    val previewTag = AssColorConverter.formatColorOverrideTag(
        tagType = tagType,
        hexColor = currentHex,
        alpha = alphaInt,
    )
    val activeComposeColor = Color(
        red = rChannel / 255f,
        green = gChannel / 255f,
        blue = bChannel / 255f,
        alpha = (255 - alphaInt).coerceIn(0, 255) / 255f,
    )

    val tagDescription = when (tagType) {
        "\\2c" -> "\\2c (Secondary / Karaoke Fill)"
        "\\3c" -> "\\3c (Outline / Border Color)"
        "\\4c" -> "\\4c (Shadow Color)"
        else -> "\\c (Primary Fill Color)"
    }

    val presetSwatches = remember {
        listOf(
            "#FFFFFF", "#38BDF8", "#F43F5E", "#22C55E",
            "#F59E0B", "#EAB308", "#A855F7", "#EC4899",
            "#00FFFF", "#0000FF", "#FF0000", "#000000",
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgPanel)
                .border(1.dp, BorderHighlight, RoundedCornerShape(14.dp)),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgModalHeader)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Color Selector",
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Tag ASS: $tagDescription",
                        color = TextMuted,
                        fontSize = 10.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = BorderColor)

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. Kotak Pratinjau Warna Aktif + Pratinjau Tag ASS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .drawBehind {
                                val tile = 7.dp.toPx()
                                val cols = (size.width / tile).toInt() + 1
                                val rows = (size.height / tile).toInt() + 1
                                for (r in 0 until rows) {
                                    for (c in 0 until cols) {
                                        drawRect(
                                            color = if ((r + c) % 2 == 0) Color(0xFF1E293B) else Color(0xFF0F172A),
                                            topLeft = Offset(c * tile, r * tile),
                                            size = Size(tile, tile),
                                        )
                                    }
                                }
                                drawRect(color = activeComposeColor)
                            }
                            .border(1.dp, BorderHighlight, RoundedCornerShape(8.dp)),
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "TAG YANG AKAN DISISIPKAN",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0D1017))
                                .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = previewTag,
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // 2. Baris Swatch Warna Cepat + Sliders RGB Visual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    presetSwatches.forEach { presetHex ->
                        val col = AssColorConverter.hexToComposeColor(presetHex)
                        val isCurrent = presetHex.equals(currentHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(col)
                                .border(
                                    width = if (isCurrent) 1.5.dp else 1.dp,
                                    color = if (isCurrent) AccentCyan else Color.White.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .clickable { syncFromHexString(presetHex) },
                        )
                    }
                }

                // RGB Sliders Kompak
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CompactRgbSliderRow(
                        channelLabel = "R",
                        value = rChannel,
                        activeColor = Color(0xFFEF4444),
                        onValueChange = { syncTextsFromRgb(it, gChannel, bChannel) },
                    )
                    CompactRgbSliderRow(
                        channelLabel = "G",
                        value = gChannel,
                        activeColor = Color(0xFF22C55E),
                        onValueChange = { syncTextsFromRgb(rChannel, it, bChannel) },
                    )
                    CompactRgbSliderRow(
                        channelLabel = "B",
                        value = bChannel,
                        activeColor = Color(0xFF38BDF8),
                        onValueChange = { syncTextsFromRgb(rChannel, gChannel, it) },
                    )
                }

                // 3. Tiga Kolom Kode Warna Tersinkronisasi (ASS &HBBGGRR&, Hex #RRGGBB, Alpha &HAA&)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Kolom 1: Format ASS (&HBBGGRR&)
                    Column(
                        modifier = Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "ASS (&HBBGGRR&)",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        SyncCodeTextField(
                            value = assBgrInputText,
                            onValueChange = ::syncFromAssBgrString,
                        )
                    }

                    // Kolom 2: Format Hex/HTML (#RRGGBB)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "HEX (#RRGGBB)",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        SyncCodeTextField(
                            value = hexInputText,
                            onValueChange = ::syncFromHexString,
                        )
                    }

                    // Kolom 3: Format Alpha (&HAA&)
                    Column(
                        modifier = Modifier.weight(0.85f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "ALPHA (&HAA&)",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        SyncCodeTextField(
                            value = alphaHexInputText,
                            onValueChange = ::syncAlphaFromHexString,
                        )
                    }
                }

                // Slider Alpha (0 = Opaque .. 255 = Transparent)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "ALPHA TRANSPARANSI (0 = Solid, 255 = Transparan)",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "$alphaInt / 255",
                            color = AccentCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Slider(
                        value = alphaInt.toFloat(),
                        onValueChange = { syncAlphaFromInt(it.roundToInt()) },
                        valueRange = 0f..255f,
                        modifier = Modifier.height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = BorderColor,
                        ),
                    )
                }
            }

            HorizontalDivider(color = BorderColor)

            // Footer: Batal & Terapkan Warna
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgModalHeader)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(BgSurface)
                        .border(1.dp, BorderColor, RoundedCornerShape(7.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Batal",
                        color = TextMain,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(AccentCyan)
                        .border(1.dp, Color(0xFF7DD3FC), RoundedCornerShape(7.dp))
                        .clickable { onApplyColorTag(previewTag) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Terapkan Warna",
                        color = BgDarkest,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactRgbSliderRow(
    channelLabel: String,
    value: Int,
    activeColor: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = channelLabel,
            color = activeColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(14.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = BorderColor,
            ),
        )
        Text(
            text = value.toString().padStart(3, ' '),
            color = TextMain,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(26.dp),
        )
    }
}

@Composable
private fun SyncCodeTextField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = TextMain,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        ),
        cursorBrush = SolidColor(AccentCyan),
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0D1017))
            .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) { inner() }
        },
    )
}
