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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.subedit.core.domain.AssScriptInfo
import com.subedit.core.domain.LAYOUT_RESOLUTION_PRESETS
import com.subedit.core.domain.SCRIPT_RESOLUTION_PRESETS
import com.subedit.core.domain.YCBCR_MATRIX_OPTIONS
import com.subedit.ui.theme.AccentCyan
import com.subedit.ui.theme.BgDarkest
import com.subedit.ui.theme.BgModalHeader
import com.subedit.ui.theme.BgPanel
import com.subedit.ui.theme.BgSurface
import com.subedit.ui.theme.BorderColor
import com.subedit.ui.theme.BorderHighlight
import com.subedit.ui.theme.TextMain
import com.subedit.ui.theme.TextMuted

private val WRAP_STYLE_LABELS = mapOf(
    0 to "0: Smart wrapping (atas lebih lebar)",
    1 to "1: End-of-line word wrapping",
    2 to "2: No word wrapping (hanya \\N)",
    3 to "3: Smart wrapping (bawah lebih lebar)",
)

/**
 * Dialog `Script Properties` (`[Script Info]`) dengan desain Floating Card gelap:
 * Mengatur `Title`, `Script Resolution` (`PlayResX` x `PlayResY`), `Layout Resolution` (`LayoutResX` x `LayoutResY`),
 * `YCbCr Matrix`, dan `WrapStyle`.
 */
@Composable
fun PropertiesDialog(
    initialScriptInfo: AssScriptInfo,
    onDismiss: () -> Unit,
    onSaveProperties: (AssScriptInfo) -> Unit,
) {
    var title by remember(initialScriptInfo) { mutableStateOf(initialScriptInfo.title) }
    var resXText by remember(initialScriptInfo) { mutableStateOf(initialScriptInfo.playResX.toString()) }
    var resYText by remember(initialScriptInfo) { mutableStateOf(initialScriptInfo.playResY.toString()) }
    var layoutResX by remember(initialScriptInfo) { mutableIntStateOf(initialScriptInfo.layoutResX) }
    var layoutResY by remember(initialScriptInfo) { mutableIntStateOf(initialScriptInfo.layoutResY) }
    var ycbcrMatrix by remember(initialScriptInfo) { mutableStateOf(initialScriptInfo.ycbcrMatrix) }
    var wrapStyle by remember(initialScriptInfo) { mutableIntStateOf(initialScriptInfo.wrapStyle.coerceIn(0, 3)) }

    var scriptPresetExpanded by remember { mutableStateOf(false) }
    var layoutPresetExpanded by remember { mutableStateOf(false) }
    var ycbcrExpanded by remember { mutableStateOf(false) }
    var wrapExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
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
                        text = "Script Properties",
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Konfigurasi Resolusi Kanvas & [Script Info]",
                        color = TextMuted,
                        fontSize = 10.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = BorderColor)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Title
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TITLE SKRIP", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    PropTextField(value = title, onValueChange = { title = it })
                }

                // Script Resolution Preset Dropdown
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "SCRIPT RESOLUTION PRESET",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val matchedPreset = SCRIPT_RESOLUTION_PRESETS.firstOrNull {
                        it.width.toString() == resXText && it.height.toString() == resYText
                    }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0D1017))
                                .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                .clickable { scriptPresetExpanded = true }
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = matchedPreset?.label ?: "${resXText}x${resYText} (Custom)",
                                color = TextMain,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("▾", color = TextMuted, fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = scriptPresetExpanded,
                            onDismissRequest = { scriptPresetExpanded = false },
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .background(BgSurface),
                        ) {
                            SCRIPT_RESOLUTION_PRESETS.forEach { preset ->
                                val isCurr = preset.width.toString() == resXText && preset.height.toString() == resYText
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = preset.label,
                                            color = if (isCurr) AccentCyan else TextMain,
                                            fontSize = 11.sp,
                                        )
                                    },
                                    onClick = {
                                        resXText = preset.width.toString()
                                        resYText = preset.height.toString()
                                        scriptPresetExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // PlayResX & PlayResY
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PLAYRES X (WIDTH)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        PropTextField(
                            value = resXText,
                            onValueChange = { resXText = it },
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PLAYRES Y (HEIGHT)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        PropTextField(
                            value = resYText,
                            onValueChange = { resYText = it },
                            keyboardType = KeyboardType.Number,
                        )
                    }
                }

                // Layout Resolution Dropdown
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("LAYOUT RESOLUTION", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val matchedLayout = LAYOUT_RESOLUTION_PRESETS.firstOrNull {
                        it.width == layoutResX && it.height == layoutResY
                    }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0D1017))
                                .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                .clickable { layoutPresetExpanded = true }
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = matchedLayout?.label ?: "${layoutResX}x${layoutResY}",
                                color = TextMain,
                                fontSize = 11.sp,
                            )
                            Text("▾", color = TextMuted, fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = layoutPresetExpanded,
                            onDismissRequest = { layoutPresetExpanded = false },
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .background(BgSurface),
                        ) {
                            LAYOUT_RESOLUTION_PRESETS.forEach { preset ->
                                val isCurr = preset.width == layoutResX && preset.height == layoutResY
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = preset.label,
                                            color = if (isCurr) AccentCyan else TextMain,
                                            fontSize = 11.sp,
                                        )
                                    },
                                    onClick = {
                                        layoutResX = preset.width
                                        layoutResY = preset.height
                                        layoutPresetExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // YCbCr Matrix Dropdown
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("YCBCR MATRIX", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0D1017))
                                .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                .clickable { ycbcrExpanded = true }
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (ycbcrMatrix == "TV.709") "TV.709 (Recommended)" else ycbcrMatrix,
                                color = TextMain,
                                fontSize = 11.sp,
                            )
                            Text("▾", color = TextMuted, fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = ycbcrExpanded,
                            onDismissRequest = { ycbcrExpanded = false },
                            modifier = Modifier.background(BgSurface),
                        ) {
                            YCBCR_MATRIX_OPTIONS.forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = opt.displayLabel,
                                            color = if (opt.value == ycbcrMatrix) AccentCyan else TextMain,
                                            fontSize = 11.sp,
                                        )
                                    },
                                    onClick = {
                                        ycbcrMatrix = opt.value
                                        ycbcrExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // WrapStyle
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("WRAP STYLE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0D1017))
                                .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                .clickable { wrapExpanded = true }
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = WRAP_STYLE_LABELS[wrapStyle] ?: "0: Smart wrapping",
                                color = TextMain,
                                fontSize = 11.sp,
                            )
                            Text("▾", color = TextMuted, fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = wrapExpanded,
                            onDismissRequest = { wrapExpanded = false },
                            modifier = Modifier.background(BgSurface),
                        ) {
                            WRAP_STYLE_LABELS.forEach { (code, desc) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = desc,
                                            color = if (code == wrapStyle) AccentCyan else TextMain,
                                            fontSize = 11.sp,
                                        )
                                    },
                                    onClick = {
                                        wrapStyle = code
                                        wrapExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderColor)

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgModalHeader)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
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
                        .clickable {
                            onSaveProperties(
                                initialScriptInfo.copy(
                                    title = title.trim().ifEmpty { "SubEdit Project" },
                                    playResX = (resXText.toIntOrNull() ?: 1920).coerceIn(320, 7680),
                                    playResY = (resYText.toIntOrNull() ?: 1080).coerceIn(240, 4320),
                                    layoutResX = layoutResX,
                                    layoutResY = layoutResY,
                                    ycbcrMatrix = ycbcrMatrix,
                                    wrapStyle = wrapStyle,
                                )
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Simpan", color = BgDarkest, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun PropTextField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = TextMain, fontSize = 12.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(AccentCyan),
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0D1017))
            .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) { inner() }
        },
    )
}
