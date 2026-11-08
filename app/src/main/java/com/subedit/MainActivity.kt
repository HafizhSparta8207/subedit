package com.subedit

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.subedit.core.domain.AssProjectItem
import com.subedit.core.domain.KeyframeEngine
import com.subedit.core.domain.SubtitleParser
import com.subedit.ui.editor.EditorScreen
import com.subedit.ui.home.HomeScreen
import com.subedit.ui.theme.SubEditTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SubEditTheme {
                // State Awal Bersih (First Install): Daftar riwayat proyek awal kosong []
                val projects = remember {
                    mutableStateListOf<AssProjectItem>().apply {
                        addAll(AssProjectItem.defaultProjects())
                    }
                }

                var activeProject by remember { mutableStateOf<AssProjectItem?>(null) }
                var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
                var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
                var formatErrorMessage by remember { mutableStateOf<String?>(null) }
                var pendingAssExportContent by remember { mutableStateOf("") }

                // 1. File Picker Subtitle (.ass & .srt, validasi ekstensi dengan Error Dialog)
                val subtitlePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri: Uri? ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val fileName = resolveDisplayName(this, uri) ?: uri.lastPathSegment.orEmpty()
                    if (!SubtitleParser.isSupportedSubtitleFileName(fileName)) {
                        formatErrorMessage =
                            "Format tidak didukung! SubEdit hanya mendukung file .ass dan .srt (Dipilih: $fileName)"
                        return@rememberLauncherForActivityResult
                    }

                    runCatching {
                        val rawContent = contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                            ?: error("Gagal membuka stream file subtitle.")

                        val parsedDoc = SubtitleParser.parseSubtitleContent(fileName, rawContent)
                        val currentProj = activeProject ?: AssProjectItem.createZeroStateProject()
                        val updatedProj = currentProj.copy(
                            title = fileName.substringBeforeLast('.').ifEmpty { parsedDoc.scriptInfo.title },
                            lineCount = parsedDoc.events.size,
                            scriptInfo = parsedDoc.scriptInfo,
                            styles = parsedDoc.styles,
                            events = parsedDoc.events,
                        )
                        activeProject = updatedProj
                    }.onFailure { err ->
                        formatErrorMessage = "Gagal membaca file subtitle: ${err.localizedMessage}"
                    }
                }

                // 2. File Picker Video (.mp4, .webm, .mkv)
                val videoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri: Uri? ->
                    if (uri != null) {
                        selectedVideoUri = uri
                    }
                }

                // 3. File Picker Audio (.mp3, .wav, .m4a, .aac, .flac)
                val audioPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri: Uri? ->
                    if (uri != null) {
                        selectedAudioUri = uri
                    }
                }

                // 4. File Picker Keyframes (.txt / .log)
                val keyframePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri: Uri? ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    runCatching {
                        val content = contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                            .orEmpty()
                        val parsedKf = KeyframeEngine.parseKeyframesTxt(content)
                        if (parsedKf.isNotEmpty()) {
                            activeProject = activeProject?.copy(keyframesUs = parsedKf)
                            Toast.makeText(
                                this,
                                "Dimuat ${parsedKf.size} titik keyframe ungu",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            formatErrorMessage = "File keyframe (.txt/.log) tidak berisi daftar frame/waktu yang valid."
                        }
                    }
                }

                // 5. File Saver Export .ASS
                val exportAssLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain"),
                ) { targetUri: Uri? ->
                    if (targetUri != null && pendingAssExportContent.isNotEmpty()) {
                        runCatching {
                            contentResolver.openOutputStream(targetUri)?.use { out ->
                                out.write(pendingAssExportContent.toByteArray(Charsets.UTF_8))
                            }
                            Toast.makeText(
                                this,
                                "Berhasil menyimpan skrip .ASS",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }

                val currentProject = activeProject
                if (currentProject == null) {
                    HomeScreen(
                        projects = projects,
                        onOpenProject = { clickedProject ->
                            selectedVideoUri = null
                            selectedAudioUri = null
                            activeProject = clickedProject
                        },
                        onCreateNewProject = { newZeroStateProject ->
                            // Buka editor dengan Zero-State bersih; proyek baru masuk riwayat setelah disimpan
                            selectedVideoUri = null
                            selectedAudioUri = null
                            activeProject = newZeroStateProject
                        },
                    )
                } else {
                    BackHandler {
                        activeProject = null
                    }

                    EditorScreen(
                        project = currentProject,
                        videoUri = selectedVideoUri,
                        audioUri = selectedAudioUri,
                        externalErrorMessage = formatErrorMessage,
                        onClearExternalError = { formatErrorMessage = null },
                        onBackToHome = { activeProject = null },
                        onRequestOpenSubtitle = {
                            subtitlePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onRequestOpenVideo = {
                            videoPickerLauncher.launch(
                                arrayOf("video/mp4", "video/webm", "video/x-matroska", "video/*")
                            )
                        },
                        onRequestOpenAudio = {
                            audioPickerLauncher.launch(
                                arrayOf("audio/mpeg", "audio/wav", "audio/mp4", "audio/aac", "audio/flac", "audio/*")
                            )
                        },
                        onRequestLoadKeyframes = {
                            keyframePickerLauncher.launch(arrayOf("text/plain", "*/*"))
                        },
                        onSaveAssFile = { scriptInfo, styles, events ->
                            // Proyek baru muncul di riwayat Beranda setelah dibuat dan disimpan oleh pengguna
                            val updatedItem = currentProject.copy(
                                title = scriptInfo.title,
                                lineCount = events.size,
                                scriptInfo = scriptInfo,
                                styles = styles,
                                events = events,
                            )
                            activeProject = updatedItem
                            val idx = projects.indexOfFirst { it.id == updatedItem.id }
                            if (idx != -1) {
                                projects[idx] = updatedItem
                            } else {
                                projects.add(0, updatedItem)
                            }

                            pendingAssExportContent = SubtitleParser.exportToAss(
                                scriptInfo = scriptInfo,
                                styles = styles,
                                events = events,
                            )
                            exportAssLauncher.launch("${scriptInfo.title.replace(' ', '_')}.ass")
                        },
                    )
                }
            }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
    }.getOrNull()
}
