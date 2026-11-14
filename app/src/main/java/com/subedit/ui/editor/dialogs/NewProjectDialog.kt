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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.subedit.core.domain.AssProjectItem
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

@Composable
fun NewProjectDialog(
    nextProjectIndex: Int,
    onDismiss: () -> Unit,
    onConfirmNewProject: (AssProjectItem) -> Unit,
) {
    val defaultName = remember(nextProjectIndex) { "Project_Subtitle_$nextProjectIndex" }
    var projectName by remember(defaultName) { mutableStateOf(defaultName) }

    val defaultScriptResIdx = remember {
        SCRIPT_RESOLUTION_PRESETS.indexOfFirst { it.width == 1920 && it.height == 1080 }
            .coerceAtLeast(0)
    }
    var selectedScriptResIdx by remember { mutableIntStateOf(defaultScriptResIdx) }
    var scriptResExpanded by remember { mutableStateOf(false) }

    var selectedLayoutResIdx by remember { mutableIntStateOf(0) }
    var layoutResExpanded by remember { mutableStateOf(false) }

    var selectedYcbcrIdx by remember { mutableIntStateOf(0) }
    var ycbcrExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgPanel)
                .border(1.dp, BorderHighlight, RoundedCornerShape(14.dp)),
        ) {
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
                        text = "New Project Properties",
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Inisialisasi Skrip ASS Baru ([Script Info])",
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "PROJECT NAME",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    BasicTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextMain, fontSize = 12.sp),
                        cursorBrush = SolidColor(AccentCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0D1017))
                            .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (projectName.isEmpty()) {
                                    Text(
                                        text = defaultName,
                                        color = Color(0xFF64748B),
                                        fontSize = 12.sp,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }

                val currentScriptPreset = SCRIPT_RESOLUTION_PRESETS[selectedScriptResIdx]
                NewProjectDropdownField(
                    label = "SCRIPT RESOLUTION (PLAYRESX × PLAYRESY)",
                    selectedLabel = if (currentScriptPreset.width == 1920 && currentScriptPreset.height == 1080) {
                        "${currentScriptPreset.label} [Default]"
                    } else {
                        currentScriptPreset.label
                    },
                    expanded = scriptResExpanded,
                    onExpandChange = { scriptResExpanded = it },
                    options = SCRIPT_RESOLUTION_PRESETS.map { preset ->
                        if (preset.width == 1920 && preset.height == 1080) {
                            "${preset.label} [Default]"
                        } else {
                            preset.label
                        }
                    },
                    selectedIndex = selectedScriptResIdx,
                    onSelectIndex = { idx ->
                        selectedScriptResIdx = idx
                        scriptResExpanded = false
                    },
                )

                val currentLayoutPreset = LAYOUT_RESOLUTION_PRESETS[selectedLayoutResIdx]
                NewProjectDropdownField(
                    label = "LAYOUT RESOLUTION",
                    selectedLabel = currentLayoutPreset.label,
                    expanded = layoutResExpanded,
                    onExpandChange = { layoutResExpanded = it },
                    options = LAYOUT_RESOLUTION_PRESETS.map { it.label },
                    selectedIndex = selectedLayoutResIdx,
                    onSelectIndex = { idx ->
                        selectedLayoutResIdx = idx
                        layoutResExpanded = false
                    },
                )

                val currentYcbcr = YCBCR_MATRIX_OPTIONS[selectedYcbcrIdx]
                NewProjectDropdownField(
                    label = "YCBCR MATRIX",
                    selectedLabel = formatYcbcrLabel(currentYcbcr.value),
                    expanded = ycbcrExpanded,
                    onExpandChange = { ycbcrExpanded = it },
                    options = YCBCR_MATRIX_OPTIONS.map { formatYcbcrLabel(it.value) },
                    selectedIndex = selectedYcbcrIdx,
                    onSelectIndex = { idx ->
                        selectedYcbcrIdx = idx
                        ycbcrExpanded = false
                    },
                )
            }

            HorizontalDivider(color = BorderColor)

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
                        .clickable {
                            val scriptRes = SCRIPT_RESOLUTION_PRESETS[selectedScriptResIdx]
                            val layoutRes = LAYOUT_RESOLUTION_PRESETS[selectedLayoutResIdx]
                            val matrixVal = YCBCR_MATRIX_OPTIONS[selectedYcbcrIdx].value
                            val finalTitle = projectName.trim().ifEmpty { defaultName }

                            val zeroStateProject = AssProjectItem.createZeroStateProject(
                                projectIndex = nextProjectIndex,
                                title = finalTitle,
                                playResX = scriptRes.width,
                                playResY = scriptRes.height,
                                layoutResX = layoutRes.width,
                                layoutResY = layoutRes.height,
                                ycbcrMatrix = matrixVal,
                            )
                            onConfirmNewProject(zeroStateProject)
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "OK",
                        color = BgDarkest,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

private fun formatYcbcrLabel(option: String): String = when (option) {
    "None" -> "None [Default]"
    "TV.709" -> "TV.709 (Recommended)"
    else -> option
}

@Composable
private fun NewProjectDropdownField(
    label: String,
    selectedLabel: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0D1017))
                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                    .clickable { onExpandChange(true) }
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLabel,
                    color = TextMain,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("▾", color = TextMuted, fontSize = 12.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandChange(false) },
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .background(BgSurface)
                    .border(1.dp, BorderHighlight, RoundedCornerShape(8.dp)),
            ) {
                options.forEachIndexed { idx, text ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = text,
                                color = if (idx == selectedIndex) AccentCyan else TextMain,
                                fontSize = 11.sp,
                                fontWeight = if (idx == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = { onSelectIndex(idx) },
                    )
                }
            }
        }
    }
}
