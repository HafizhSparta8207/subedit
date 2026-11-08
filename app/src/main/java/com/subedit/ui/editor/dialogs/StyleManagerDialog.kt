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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.subedit.core.domain.AssStyle
import com.subedit.core.font.FontRepository
import com.subedit.ui.theme.AccentCoral
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
 * Modal 1: `StyleManagerDialog` dengan desain Floating Card berlatar gelap sesuai `editor.html`.
 *
 * Fitur:
 * - Menampilkan daftar seluruh style `[V4+ Styles]` di dalam file subtitle aktif.
 * - Tombol urutkan `▲` / `▼`, tombol `✏️` (Buka `StyleEditorDialog`), dan tombol `❌` (Hapus Style).
 * - Tombol lebar `➕ Add Style` dengan border garis putus-putus di bawah daftar list.
 * - Tombol `Batal` dan `Simpan`.
 */
@Composable
fun StyleManagerDialog(
    initialStyles: List<AssStyle>,
    activeStyleName: String,
    onDismiss: () -> Unit,
    onSaveStyles: (updatedStyles: List<AssStyle>, renamedMap: Map<String, String>) -> Unit,
) {
    val draftStyles = remember(initialStyles) {
        mutableStateListOf<AssStyle>().apply { addAll(initialStyles) }
    }
    val originalNames = remember(initialStyles) {
        mutableStateListOf<String>().apply { addAll(initialStyles.map { it.name }) }
    }

    var editingIndex by remember { mutableIntStateOf(-2) } // -2 = tertutup, -1 = tambah baru, >=0 = edit
    var minimumStyleWarning by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 580.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgPanel)
                .border(1.dp, BorderHighlight, RoundedCornerShape(14.dp)),
        ) {
            // Header Floating Card
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
                        text = "Style Manager",
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Daftar Style Skrip Aktif ([V4+ Styles])",
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
                    Text(
                        text = "✕",
                        color = TextMain,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            HorizontalDivider(color = BorderColor)

            // Body: Daftar Style + Tombol Add Style
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (minimumStyleWarning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentCoral.copy(alpha = 0.16f))
                            .border(1.dp, AccentCoral, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Skrip harus memiliki minimal 1 style aktif.",
                            color = AccentCoral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                draftStyles.forEachIndexed { index, style ->
                    val isCurrentActive = style.name == activeStyleName
                    StyleRowItem(
                        style = style,
                        isActiveStyle = isCurrentActive,
                        onMoveUp = {
                            if (index > 0) {
                                val s = draftStyles.removeAt(index)
                                draftStyles.add(index - 1, s)
                                val orig = originalNames.removeAt(index)
                                originalNames.add(index - 1, orig)
                            }
                        },
                        onMoveDown = {
                            if (index < draftStyles.lastIndex) {
                                val s = draftStyles.removeAt(index)
                                draftStyles.add(index + 1, s)
                                val orig = originalNames.removeAt(index)
                                originalNames.add(index + 1, orig)
                            }
                        },
                        onEditClick = {
                            minimumStyleWarning = false
                            editingIndex = index
                        },
                        onDeleteClick = {
                            if (draftStyles.size <= 1) {
                                minimumStyleWarning = true
                            } else {
                                minimumStyleWarning = false
                                draftStyles.removeAt(index)
                                originalNames.removeAt(index)
                            }
                        },
                    )
                }

                // Tombol Lebar "➕ Add Style" dengan Dashed Border
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentCyan.copy(alpha = 0.08f))
                        .drawWithCache {
                            val stroke = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRoundRect(
                                    color = AccentCyan,
                                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                                    style = stroke,
                                )
                            }
                        }
                        .clickable {
                            minimumStyleWarning = false
                            editingIndex = -1
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "➕ Add Style",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            HorizontalDivider(color = BorderColor)

            // Footer: Batal & Simpan
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
                            val renamedMap = mutableMapOf<String, String>()
                            for (i in draftStyles.indices) {
                                val orig = originalNames.getOrNull(i)
                                val curr = draftStyles[i].name
                                if (orig != null && orig != curr) {
                                    renamedMap[orig] = curr
                                }
                            }
                            onSaveStyles(draftStyles.toList(), renamedMap)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Simpan",
                        color = BgDarkest,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }

    // Jika pengguna menekan "✏️" atau "➕ Add Style", tampilkan StyleEditorDialog di atasnya
    if (editingIndex != -2) {
        val initialTarget = if (editingIndex >= 0 && editingIndex in draftStyles.indices) {
            draftStyles[editingIndex]
        } else {
            AssStyle(name = "Default Custom ${draftStyles.size + 1}")
        }
        StyleEditorDialog(
            initialStyle = initialTarget,
            isNewStyle = editingIndex == -1,
            onDismiss = { editingIndex = -2 },
            onApplyStyle = { updated ->
                if (editingIndex >= 0 && editingIndex in draftStyles.indices) {
                    draftStyles[editingIndex] = updated
                } else {
                    draftStyles.add(updated)
                    originalNames.add(updated.name)
                }
                editingIndex = -2
            },
        )
    }
}

@Composable
private fun StyleRowItem(
    style: AssStyle,
    isActiveStyle: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val borderCol = if (isActiveStyle) AccentCyan.copy(alpha = 0.55f) else BorderColor
    val bgCol = if (isActiveStyle) AccentCyan.copy(alpha = 0.06f) else Color(0xFF12161F)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgCol)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Tombol Urutkan Atas / Bawah
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background( Color(0xFF1E2430))
                    .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(3.dp))
                    .clickable(onClick = onMoveUp)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text("▲", color = TextMuted, fontSize = 8.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E2430))
                    .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(3.dp))
                    .clickable(onClick = onMoveDown)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text("▼", color = TextMuted, fontSize = 8.sp)
            }
        }

        // Info Nama & Ringkasan Style
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val assetFontFamily = FontRepository.rememberAssFontFamily(style.fontname)
            Text(
                text = style.name,
                color = Color(0xFFF8FAFC),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = assetFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val flags = buildList {
                if (style.bold) add("Bold")
                if (style.italic) add("Italic")
            }
            val flagStr = if (flags.isNotEmpty()) " • ${flags.joinToString("/")}" else ""
            Text(
                text = "${style.fontname}, ${style.fontsize.toInt()}pt$flagStr • \\an${style.alignment}",
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Tombol Aksi Kanan: ✏️ (Edit) & ❌ (Hapus)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BgSurface)
                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                    .clickable(onClick = onEditClick),
                contentAlignment = Alignment.Center,
            ) {
                Text("✏️", fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BgSurface)
                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center,
            ) {
                Text("❌", fontSize = 12.sp)
            }
        }
    }
}
