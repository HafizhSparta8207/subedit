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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.subedit.core.domain.ASS_ALIGNMENT_LABELS
import com.subedit.core.domain.AssColorConverter
import com.subedit.core.domain.AssStyle
import com.subedit.core.domain.CRUNCHYROLL_STANDARD_FONTS
import com.subedit.core.font.FontRepository
import com.subedit.ui.theme.AccentCyan
import com.subedit.ui.theme.BgDarkest
import com.subedit.ui.theme.BgModalHeader
import com.subedit.ui.theme.BgPanel
import com.subedit.ui.theme.BgSurface
import com.subedit.ui.theme.BorderColor
import com.subedit.ui.theme.BorderHighlight
import com.subedit.ui.theme.TextMain
import com.subedit.ui.theme.TextMuted

/**
 * Modal 2: `StyleEditorDialog` (Floating Card Design) sesuai `editor.html` & spesifikasi:
 * - Header: Style Name input.
 * - Font Family: Dropdown memuat 12 font standar Crunchyroll.
 * - Font Size: Input angka.
 * - Checkbox / Toggle Formatting: `Bold`, `Italic`, `Underline`, `Strikeout`.
 * - 4 Palet Warna: `Primary`, `Secondary`, `Outline`, `Shadow` (Hex <-> ASS `&H00BBGGRR`).
 * - Margins: `Left`, `Right`, `Vert`.
 * - `Outline` & `Shadow` thickness: Input angka.
 * - **Alignment Matrix**: Keypad 3x3 numpad (`[7][8][9]`, `[4][5][6]`, `[1][2][3]`) diletakkan
 *   presisi di posisi rata tengah.
 * - **Tanpa Kolom Encoding** (dikunci internal ke `1` / Default UTF-8).
 * - **Live Preview Box**: Kotak pratinjau teks real-time dengan style yang sedang diubah.
 * - Tombol `Batal` dan `Terapkan Style`.
 */
@Composable
fun StyleEditorDialog(
    initialStyle: AssStyle,
    isNewStyle: Boolean = false,
    onDismiss: () -> Unit,
    onApplyStyle: (AssStyle) -> Unit,
) {
    var styleName by remember(initialStyle) { mutableStateOf(initialStyle.name) }
    var fontName by remember(initialStyle) { mutableStateOf(initialStyle.fontname) }
    var fontSizeText by remember(initialStyle) { mutableStateOf(initialStyle.fontsize.toInt().toString()) }

    var bold by remember(initialStyle) { mutableStateOf(initialStyle.bold) }
    var italic by remember(initialStyle) { mutableStateOf(initialStyle.italic) }
    var underline by remember(initialStyle) { mutableStateOf(initialStyle.underline) }
    var strikeout by remember(initialStyle) { mutableStateOf(initialStyle.strikeout) }

    var primaryHex by remember(initialStyle) { mutableStateOf(initialStyle.primaryColorHex) }
    var secondaryHex by remember(initialStyle) { mutableStateOf(initialStyle.secondaryColorHex) }
    var outlineHex by remember(initialStyle) { mutableStateOf(initialStyle.outlineColorHex) }
    var shadowHex by remember(initialStyle) { mutableStateOf(initialStyle.shadowColorHex) }

    var marginLText by remember(initialStyle) { mutableStateOf(initialStyle.marginL.toString()) }
    var marginRText by remember(initialStyle) { mutableStateOf(initialStyle.marginR.toString()) }
    var marginVText by remember(initialStyle) { mutableStateOf(initialStyle.marginV.toString()) }

    var outlineWidthText by remember(initialStyle) { mutableStateOf(initialStyle.outline.toString()) }
    var shadowDepthText by remember(initialStyle) { mutableStateOf(initialStyle.shadow.toString()) }

    var alignment by remember(initialStyle) { mutableIntStateOf(initialStyle.alignment.coerceIn(1, 9)) }

    var fontDropdownExpanded by remember { mutableStateOf(false) }
    var activeColorPickerSlot by remember { mutableStateOf<String?>(null) }

    val previewStyle = remember(
        styleName, fontName, fontSizeText, bold, italic, underline, strikeout,
        primaryHex, secondaryHex, outlineHex, shadowHex,
        marginLText, marginRText, marginVText, outlineWidthText, shadowDepthText, alignment
    ) {
        AssStyle(
            name = styleName.trim().ifEmpty { "DefaultCR Style" }.replace(',', ' '),
            fontname = fontName,
            fontsize = (fontSizeText.toFloatOrNull() ?: 48f).coerceIn(8f, 200f),
            primaryColorHex = primaryHex,
            secondaryColorHex = secondaryHex,
            outlineColorHex = outlineHex,
            shadowColorHex = shadowHex,
            bold = bold,
            italic = italic,
            underline = underline,
            strikeout = strikeout,
            outline = (outlineWidthText.toFloatOrNull() ?: 2.5f).coerceIn(0f, 20f),
            shadow = (shadowDepthText.toFloatOrNull() ?: 1.5f).coerceIn(0f, 20f),
            alignment = alignment,
            marginL = (marginLText.toIntOrNull() ?: 60).coerceIn(0, 900),
            marginR = (marginRText.toIntOrNull() ?: 60).coerceIn(0, 900),
            marginV = (marginVText.toIntOrNull() ?: 45).coerceIn(0, 900),
            encoding = 1, // Terkunci internal ke 1 (UTF-8)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 650.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgPanel)
                .border(1.dp, BorderHighlight, RoundedCornerShape(14.dp)),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgModalHeader)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Style Editor",
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = if (isNewStyle) "Tambah Style Baru" else "Mengedit: ${initialStyle.name}",
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

            // Body Scrollable
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. Style Name
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StyleFieldLabel("STYLE NAME")
                    CompactDialogTextField(
                        value = styleName,
                        onValueChange = { styleName = it },
                        placeholder = "Contoh: DefaultCR Default",
                    )
                }

                // 2. Font Family (Crunchyroll Standard) & Font Size (pt)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        StyleFieldLabel("FONT FAMILY (CRUNCHYROLL STANDARD)")
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0D1017))
                                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                    .clickable { fontDropdownExpanded = true }
                                    .padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = fontName,
                                    color = TextMain,
                                    fontSize = 12.sp,
                                )
                                Text("▾", color = TextMuted, fontSize = 12.sp)
                            }

                            DropdownMenu(
                                expanded = fontDropdownExpanded,
                                onDismissRequest = { fontDropdownExpanded = false },
                                modifier = Modifier.background(BgSurface),
                            ) {
                                val allFonts = remember(initialStyle.fontname) {
                                    if (initialStyle.fontname in CRUNCHYROLL_STANDARD_FONTS) {
                                        CRUNCHYROLL_STANDARD_FONTS
                                    } else {
                                        listOf(initialStyle.fontname) + CRUNCHYROLL_STANDARD_FONTS
                                    }
                                }
                                allFonts.forEach { fontOption ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = fontOption,
                                                color = if (fontOption == fontName) AccentCyan else TextMain,
                                                fontSize = 12.sp,
                                            )
                                        },
                                        onClick = {
                                            fontName = fontOption
                                            fontDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.width(88.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        StyleFieldLabel("FONT SIZE (PT)")
                        CompactDialogTextField(
                            value = fontSizeText,
                            onValueChange = { fontSizeText = it },
                            keyboardType = KeyboardType.Number,
                        )
                    }
                }

                // 3. Text Formatting Toggles (Bold, Italic, Underline, Strikeout)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StyleFieldLabel("TEXT FORMATTING")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FormattingToggleChip(
                            label = "B Bold",
                            selected = bold,
                            onClick = { bold = !bold },
                            modifier = Modifier.weight(1f),
                        )
                        FormattingToggleChip(
                            label = "I Italic",
                            selected = italic,
                            onClick = { italic = !italic },
                            modifier = Modifier.weight(1f),
                        )
                        FormattingToggleChip(
                            label = "U Under",
                            selected = underline,
                            onClick = { underline = !underline },
                            modifier = Modifier.weight(1f),
                        )
                        FormattingToggleChip(
                            label = "S Strike",
                            selected = strikeout,
                            onClick = { strikeout = !strikeout },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 4. 4 Palet Warna (Primary, Secondary, Outline, Shadow)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StyleFieldLabel("COLOR PALETTE (HEX ↔ ASS &H00BBGGRR&)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ColorSwatchBox(
                            title = "Primary",
                            hexColor = primaryHex,
                            onClick = { activeColorPickerSlot = "primary" },
                            modifier = Modifier.weight(1f),
                        )
                        ColorSwatchBox(
                            title = "Secondary",
                            hexColor = secondaryHex,
                            onClick = { activeColorPickerSlot = "secondary" },
                            modifier = Modifier.weight(1f),
                        )
                        ColorSwatchBox(
                            title = "Outline",
                            hexColor = outlineHex,
                            onClick = { activeColorPickerSlot = "outline" },
                            modifier = Modifier.weight(1f),
                        )
                        ColorSwatchBox(
                            title = "Shadow",
                            hexColor = shadowHex,
                            onClick = { activeColorPickerSlot = "shadow" },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 5. Margins (Left, Right, Vert)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StyleFieldLabel("MARGINS (PX PADA PLAYRES)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            StyleFieldLabel("LEFT")
                            CompactDialogTextField(
                                value = marginLText,
                                onValueChange = { marginLText = it },
                                keyboardType = KeyboardType.Number,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            StyleFieldLabel("RIGHT")
                            CompactDialogTextField(
                                value = marginRText,
                                onValueChange = { marginRText = it },
                                keyboardType = KeyboardType.Number,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            StyleFieldLabel("VERT")
                            CompactDialogTextField(
                                value = marginVText,
                                onValueChange = { marginVText = it },
                                keyboardType = KeyboardType.Number,
                            )
                        }
                    }
                }

                // 6. Outline & Shadow Thickness
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StyleFieldLabel("OUTLINE THICKNESS")
                        CompactDialogTextField(
                            value = outlineWidthText,
                            onValueChange = { outlineWidthText = it },
                            keyboardType = KeyboardType.Decimal,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StyleFieldLabel("SHADOW DISTANCE")
                        CompactDialogTextField(
                            value = shadowDepthText,
                            onValueChange = { shadowDepthText = it },
                            keyboardType = KeyboardType.Decimal,
                        )
                    }
                }

                // 7. Alignment Matrix: Keypad 3x3 Numpad ([7][8][9], [4][5][6], [1][2][3]) Rata Tengah
                // (Tanpa kolom Encoding; dikunci internal ke 1 / Default UTF-8)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StyleFieldLabel("ALIGNMENT MATRIX (\\AN1 - \\AN9)")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF12161F))
                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val rows = listOf(
                            listOf(7, 8, 9),
                            listOf(4, 5, 6),
                            listOf(1, 2, 3),
                        )
                        for (row in rows) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                for (num in row) {
                                    val isSelected = alignment == num
                                    Box(
                                        modifier = Modifier
                                            .size(width = 52.dp, height = 32.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(if (isSelected) AccentCyan else Color(0xFF1B2230))
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color.White else Color(0xFF2D3748),
                                                shape = RoundedCornerShape(5.dp),
                                            )
                                            .clickable { alignment = num },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = num.toString(),
                                            color = if (isSelected) BgDarkest else TextMuted,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = ASS_ALIGNMENT_LABELS[alignment] ?: "\\an2 (Bottom Center)",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // 8. Live Preview Box (Kotak Hitam Checkerboard Real-Time)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StyleFieldLabel("LIVE PREVIEW (REAL-TIME STYLE RENDER)")
                    StyleLivePreviewBox(style = previewStyle)
                }
            }

            HorizontalDivider(color = BorderColor)

            // Footer: Batal & Terapkan Style
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
                    Text("Batal", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(AccentCyan)
                        .border(1.dp, Color(0xFF7DD3FC), RoundedCornerShape(7.dp))
                        .clickable { onApplyStyle(previewStyle) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Terapkan Style",
                        color = BgDarkest,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }

    // Sub-dialog Pemilih Warna Cepat + Input Hex
    val slot = activeColorPickerSlot
    if (slot != null) {
        val currentHex = when (slot) {
            "primary" -> primaryHex
            "secondary" -> secondaryHex
            "outline" -> outlineHex
            else -> shadowHex
        }
        ColorPickerModal(
            title = "Pilih Warna ${slot.replaceFirstChar { it.uppercase() }}",
            initialHex = currentHex,
            onDismiss = { activeColorPickerSlot = null },
            onSelectHex = { chosenHex ->
                when (slot) {
                    "primary" -> primaryHex = chosenHex
                    "secondary" -> secondaryHex = chosenHex
                    "outline" -> outlineHex = chosenHex
                    "shadow" -> shadowHex = chosenHex
                }
                activeColorPickerSlot = null
            },
        )
    }
}

@Composable
private fun StyleLivePreviewBox(
    style: AssStyle,
) {
    val previewFontSize = (style.fontsize * 0.42f).coerceIn(13f, 32f).sp
    val textDecorations = buildList {
        if (style.underline) add(TextDecoration.Underline)
        if (style.strikeout) add(TextDecoration.LineThrough)
    }
    val combinedDecoration = if (textDecorations.isEmpty()) {
        TextDecoration.None
    } else {
        TextDecoration.combine(textDecorations)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF05070A))
            .drawBehind {
                // Gambar pola checkerboard gelap seperti di editor.html
                val tilePx = 10.dp.toPx()
                val cols = (size.width / tilePx).toInt() + 1
                val rows = (size.height / tilePx).toInt() + 1
                val checkerColor = Color(0xFF10141D)
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        if ((r + c) % 2 == 0) {
                            drawRect(
                                color = checkerColor,
                                topLeft = Offset(c * tilePx, r * tilePx),
                                size = Size(tilePx, tilePx),
                            )
                        }
                    }
                }
            }
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val sampleText = "AaBbYyZz 123"
        val fontWeight = if (style.bold) FontWeight.ExtraBold else FontWeight.SemiBold
        val fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal
        val assetFontFamily = FontRepository.rememberAssFontFamily(style.fontname)

        // Layer 1: Outline Stroke ASS
        if (style.outline > 0f) {
            Text(
                text = sampleText,
                style = TextStyle(
                    color = style.outlineComposeColor,
                    fontSize = previewFontSize,
                    fontFamily = assetFontFamily,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textAlign = TextAlign.Center,
                    drawStyle = Stroke(width = (style.outline * 2.2f).coerceAtLeast(1.5f)),
                ),
            )
        }

        // Layer 2: Fill Primary + Drop Shadow ASS
        Text(
            text = sampleText,
            style = TextStyle(
                color = style.primaryComposeColor,
                fontSize = previewFontSize,
                fontFamily = assetFontFamily,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textDecoration = combinedDecoration,
                textAlign = TextAlign.Center,
                shadow = if (style.shadow > 0f) {
                    Shadow(
                        color = style.shadowComposeColor,
                        offset = Offset(style.shadow * 1.8f, style.shadow * 1.8f),
                        blurRadius = 1.5f,
                    )
                } else {
                    null
                },
            ),
        )
    }
}

@Composable
private fun StyleFieldLabel(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
    )
}

@Composable
private fun CompactDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = TextMain,
            fontSize = 12.sp,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(AccentCyan),
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0D1017))
            .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(text = placeholder, color = Color(0xFF64748B), fontSize = 12.sp)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun FormattingToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) AccentCyan.copy(alpha = 0.16f) else Color(0xFF12161F)
    val border = if (selected) AccentCyan else BorderColor
    val textCol = if (selected) Color.White else TextMuted

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textCol,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ColorSwatchBox(
    title: String,
    hexColor: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val composeColor = AssColorConverter.hexToComposeColor(hexColor, Color.White)
    val assCode = AssColorConverter.hexToAss(hexColor, "00")

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF12161F))
            .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = Color(0xFFCBD5E1),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(composeColor)
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
        )
        Text(
            text = assCode,
            color = TextMuted,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ColorPickerModal(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onSelectHex: (String) -> Unit,
) {
    var hexInput by remember(initialHex) { mutableStateOf(initialHex.uppercase()) }
    val presetColors = remember {
        listOf(
            "#FFFFFF", "#FFFBEB", "#E2E8F0", "#94A3B8",
            "#000000", "#0F172A", "#1E293B", "#38BDF8",
            "#0000FF", "#22C55E", "#EF4444", "#F43F5E",
            "#F59E0B", "#EAB308", "#A855F7", "#EC4899",
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgPanel)
                .border(1.dp, BorderHighlight, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "✕",
                    color = TextMain,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }

            CompactDialogTextField(
                value = hexInput,
                onValueChange = { hexInput = it },
                placeholder = "#FFFFFF",
            )

            // Grid 4x4 warna cepat
            for (row in presetColors.chunked(4)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (preset in row) {
                        val c = AssColorConverter.hexToComposeColor(preset)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(c)
                                .border(
                                    width = if (preset.equals(hexInput, ignoreCase = true)) 2.dp else 1.dp,
                                    color = if (preset.equals(hexInput, ignoreCase = true)) AccentCyan else BorderHighlight,
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .clickable { onSelectHex(preset) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentCyan)
                        .clickable {
                            val normalized = if (hexInput.startsWith("#")) hexInput else "#$hexInput"
                            onSelectHex(normalized.take(7).uppercase())
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text("Pilih", color = BgDarkest, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
