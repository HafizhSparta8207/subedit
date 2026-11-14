package com.subedit.ui.editor.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.subedit.ui.theme.AccentCoral
import com.subedit.ui.theme.AccentCyan
import com.subedit.ui.theme.BgCard
import com.subedit.ui.theme.BgSurface
import com.subedit.ui.theme.BorderColor
import com.subedit.ui.theme.BorderHighlight
import com.subedit.ui.theme.TextMain

@Composable
fun HelpDialog(
    onDismiss: () -> Unit,
) {
    val helpItems = listOf(
        "↵ Enter (\\N)" to "Line Break ASS: Menekan tombol Enter pada keyboard otomatis menyisipkan tag ganti baris resmi ASS \\N dan merendernya menjadi dua baris di monitor video.",
        "➕ Open" to "Menu Media & Subtitle: Membuka file Subtitle (.ass / .srt), Video (.mp4 / .webm / .mkv), atau Audio (.mp3 / .wav / .m4a / .aac / .flac).",
        "🗑️ / ➕" to "Tombol Kontekstual: Saat baris aktif dipilih, berfungsi sebagai Hapus Line (🗑️). Ketuk area kosong pada timeline audio untuk melepas pilihan agar berubah menjadi Tambah Line (➕).",
        "⇦ / ⇨" to "Nudge Playhead Presisi: Memundurkan atau memajukan playhead audio sebesar 50ms (1 frame) untuk mencari pergantian frame / hentakan vokal tanpa menggeser baris subtitle.",
        "⇤ / ⇥" to "Shift Lead-In / Lead-Out: Menyesuaikan titik awal atau titik akhir baris aktif terhadap playhead atau menggeser ±100ms.",
        "✥ 🔄 📐 ⤢ ✂️" to "Visual Typesetting: Pilih tool di bawah timeline lalu sentuh/geser subtitle di monitor video. Tag ASS (\\pos, \\frz, \\fscx, \\clip) disuntikkan ke awal kalimat secara otomatis.",
        "\\c \\2c \\3c \\4c" to "Color Selector: Membuka modal pemilih warna tersinkronisasi (ASS &HBBGGRR&, Hex #RRGGBB, dan Alpha &HAA&) lalu menyisipkan tag warna pada posisi kursor.",
        "⋮ Menu" to "Opsi Lanjutan Subtitler: Memuat Keyframes (.txt / .log), membuka Script Properties, Style Manager, atau beralih Mode Audio / Line Grid.",
        "⚙️ Style" to "Style Manager & Editor: Pilih '⚙️ Edit Style...' pada dropdown Style untuk mengelola daftar style [V4+ Styles] berstandar Aegisub & Crunchyroll beserta Live Preview.",
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .border(1.dp, BorderHighlight, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Panduan Tombol & Fitur",
                    color = AccentCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "✕",
                    color = TextMain,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(2.dp),
                )
            }

            HorizontalDivider(color = BorderColor)

            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for ((badge, description) in helpItems) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BgSurface)
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = badge,
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Text(
                            text = description,
                            color = TextMain,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleFormatErrorDialog(
    message: String = "Format tidak didukung! SubEdit hanya mendukung file .ass dan .srt",
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .border(1.dp, AccentCoral, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⚠️ Format Tidak Didukung",
                    color = AccentCoral,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "✕",
                    color = TextMain,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }

            HorizontalDivider(color = BorderColor)

            Text(
                text = message,
                color = TextMain,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )

            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentCoral)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "Mengerti",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
