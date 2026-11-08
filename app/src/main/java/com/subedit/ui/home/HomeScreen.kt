package com.subedit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.subedit.core.domain.AssProjectItem
import com.subedit.ui.editor.dialogs.NewProjectDialog
import com.subedit.ui.theme.AccentCyan
import com.subedit.ui.theme.BgCard
import com.subedit.ui.theme.BgDarkest
import com.subedit.ui.theme.BgMain
import com.subedit.ui.theme.BgModalHeader
import com.subedit.ui.theme.BgSurface
import com.subedit.ui.theme.BorderColor
import com.subedit.ui.theme.BorderHighlight
import com.subedit.ui.theme.TextMain
import com.subedit.ui.theme.TextMuted
import com.subedit.ui.theme.TextSecondary
import com.subedit.ui.theme.TextSubtle

/**
 * Layar Utama (`HomeScreen.kt`) sesuai blueprint `index.html`.
 *
 * Fitur Utama:
 * 1. Status Bar & Header (`Modifier.statusBarsPadding()`), Brand `"SUBEDIT"`, subtitle
 *    `"Subtitle Editor for android"`, dan tombol kotak `Settings` di pojok kanan atas.
 * 2. Tombol Utama `"Add Project"` dengan ikon `+` di atas, teks `"Add Project"` di bawah,
 *    dan *dashed border* (`#334155`). Menekan tombol ini membuka modal `"New Project Properties"`
 *    (`NewProjectDialog`) dan tidak langsung masuk ke editor.
 * 3. State Awal Bersih (First Install): Saat `projects` kosong, menampilkan pesan ramah:
 *    *"Belum ada proyek subtitle. Ketuk '+ Add Project' untuk memulai."*
 * 4. Grid Riwayat Proyek Adaptif (`LazyVerticalGrid` dengan `GridCells.Adaptive(minSize = 160.dp)`).
 */
@Composable
fun HomeScreen(
    projects: List<AssProjectItem>,
    onOpenProject: (AssProjectItem) -> Unit,
    onCreateNewProject: (AssProjectItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewProjectModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var autoSnapKeyframes by remember { mutableStateOf(true) }
    var thermalEcoMode by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgMain)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxSize()
        ) {
            val isTablet = maxWidth >= 600.dp
            val horizontalPad = if (isTablet) 24.dp else 16.dp
            val gridSpacing = if (isTablet) 14.dp else 10.dp

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = horizontalPad,
                    end = horizontalPad,
                    top = if (isTablet) 18.dp else 14.dp,
                    bottom = 32.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpacing),
            ) {
                // Header Top Bar (Full Span)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeTopBar(
                        isTablet = isTablet,
                        onSettingsClick = { showSettingsModal = true },
                    )
                }

                // Tombol Utama "Add Project" dengan Dashed Border (Full Span)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        AddProjectDashedButton(
                            onClick = { showNewProjectModal = true },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                if (projects.isEmpty()) {
                    // Empty State Ramah ketika belum ada proyek tersimpan
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyProjectsNoticeCard()
                    }
                } else {
                    // Grid Kartu Riwayat Proyek
                    items(
                        items = projects,
                        key = { it.id },
                    ) { project ->
                        ProjectHistoryCard(
                            project = project,
                            onClick = { onOpenProject(project) },
                        )
                    }
                }
            }
        }
    }

    if (showNewProjectModal) {
        NewProjectDialog(
            nextProjectIndex = projects.size + 1,
            onDismiss = { showNewProjectModal = false },
            onConfirmNewProject = { newZeroStateProject ->
                showNewProjectModal = false
                onCreateNewProject(newZeroStateProject)
            },
        )
    }

    if (showSettingsModal) {
        HomeSettingsDialog(
            autoSnapKeyframes = autoSnapKeyframes,
            onAutoSnapChange = { autoSnapKeyframes = it },
            thermalEcoMode = thermalEcoMode,
            onThermalEcoChange = { thermalEcoMode = it },
            onDismiss = { showSettingsModal = false },
        )
    }
}

@Composable
private fun EmptyProjectsNoticeCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard.copy(alpha = 0.65f))
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Belum ada proyek subtitle. Ketuk '+ Add Project' untuk memulai.",
            color = TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun HomeTopBar(
    isTablet: Boolean,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "SUBEDIT",
                color = Color(0xFFF8FAFC),
                fontSize = if (isTablet) 28.sp else 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                lineHeight = if (isTablet) 30.sp else 26.sp,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Subtitle Editor for android",
                color = TextMuted,
                fontSize = if (isTablet) 13.sp else 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // Pojok kanan atas: Tombol kotak pengaturan (Settings)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BgCard)
                .border(1.dp, Color(0xFF293548), RoundedCornerShape(8.dp))
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Pengaturan",
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AddProjectDashedButton(
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BgCard)
            .drawWithCache {
                val strokeWidthPx = 1.5.dp.toPx()
                val cornerPx = 12.dp.toPx()
                val dashPx = 8.dp.toPx()
                val gapPx = 6.dp.toPx()
                val stroke = Stroke(
                    width = strokeWidthPx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx), 0f),
                )
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = BorderHighlight,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = stroke,
                    )
                }
            }
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(BgSurface)
                    .border(1.dp, BorderHighlight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Project",
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = "Add Project",
                color = TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProjectHistoryCard(
    project: AssProjectItem,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(BgCard)
            .border(1.dp, BorderColor, cardShape)
            .clickable(onClick = onClick),
    ) {
        // Thumbnail 16:9 + Badge Durasi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(BgDarkest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "16:9 PREVIEW",
                color = Color(0xFF475569),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xBF000000))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = project.durationBadge,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Body Kartu: Judul, Tanggal, Jumlah Baris
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = project.title,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = project.dateLabel,
                    color = TextSubtle,
                    fontSize = 10.sp,
                )
                Text(
                    text = "${project.lineCount} baris",
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun HomeSettingsDialog(
    autoSnapKeyframes: Boolean,
    onAutoSnapChange: (Boolean) -> Unit,
    thermalEcoMode: Boolean,
    onThermalEcoChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
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
                        text = "Pengaturan SubEdit",
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Preferensi Editor & Efisiensi Termal",
                        color = TextMuted,
                        fontSize = 10.sp,
                    )
                }
                Text(
                    text = "✕",
                    color = TextMain,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(4.dp),
                )
            }

            HorizontalDivider(color = BorderColor)

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Snap ke Keyframe Ungu",
                            color = TextMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Tarik otomatis garis Lead-In/Out ke scene cut terdekat",
                            color = TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                    Switch(
                        checked = autoSnapKeyframes,
                        onCheckedChange = onAutoSnapChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Thermal Eco Mode (50Hz PCM)",
                            color = TextMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Cache downsampled float array & hentikan loop saat pause",
                            color = TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                    Switch(
                        checked = thermalEcoMode,
                        onCheckedChange = onThermalEcoChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan),
                    )
                }
            }
        }
    }
}
