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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.subedit.core.domain.AssProjectItem
import com.subedit.core.domain.KeyframeEngine
import com.subedit.core.domain.SubtitleParser
import com.subedit.core.session.ProjectSessionManager
import com.subedit.ui.editor.EditorScreen
import com.subedit.ui.home.HomeScreen
import com.subedit.ui.theme.SubEditTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SubEditTheme {
                val projects = remember {
                    mutableStateListOf<AssProjectItem>().apply {
                        addAll(AssProjectItem.defaultProjects())
                    }
                }

                var activeProject by remember { mutableStateOf<AssProjectItem?>(null) }
                var subtitleImportVersion by remember { mutableIntStateOf(0) }
                var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
                var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
                var formatErrorMessage by remember { mutableStateOf<String?>(null) }
                var pendingAssExportContent by remember { mutableStateOf("") }

                val subtitlePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri: Uri? ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val fileName = resolveDisplayName(this@MainActivity, uri)
                                ?: uri.lastPathSegment.orEmpty()
                            if (!SubtitleParser.isSupportedSubtitleFileName(fileName)) {
                                withContext(Dispatchers.Main) {
                                    formatErrorMessage =
                                        "Format tidak didukung! SubEdit hanya mendukung file .ass dan .srt (Dipilih: $fileName)"
                                }
                                return@launch
                            }

                            val rawContent = contentResolver.openInputStream(uri)
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                ?: error("Gagal membuka stream file subtitle.")

                            val parsedDoc = SubtitleParser.parseSubtitleContent(fileName, rawContent)

                            withContext(Dispatchers.Main) {
                                ProjectSessionManager.applyImportedDocument(parsedDoc)
                                val currentProj = activeProject ?: AssProjectItem.createZeroStateProject()
                                val updatedProj = currentProj.copy(
                                    title = fileName.substringBeforeLast('.').ifEmpty { parsedDoc.scriptInfo.title },
                                    lineCount = parsedDoc.events.size,
                                    scriptInfo = parsedDoc.scriptInfo,
                                    styles = parsedDoc.styles,
                                    events = parsedDoc.events,
                                    preservedSections = parsedDoc.preservedSections,
                                )
                                activeProject = updatedProj
                                subtitleImportVersion++
                            }
                        } catch (t: Throwable) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Peringatan: Berkas subtitle rusak atau tidak dapat dibaca (${t.localizedMessage ?: "Format tidak valid"}).",
                                    Toast.LENGTH_LONG,
                                ).show()
                                formatErrorMessage =
                                    "Peringatan: Berkas subtitle tidak dapat diproses (${t.localizedMessage ?: "Format tidak valid"})."
                            }
                        }
                    }
                }

                val videoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri: Uri? ->
                    if (uri != null) {
                        selectedVideoUri = uri
                    }
                }

                val audioPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri: Uri? ->
                    if (uri != null) {
                        selectedAudioUri = uri
                    }
                }

                val keyframePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri: Uri? ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val content = contentResolver.openInputStream(uri)
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                .orEmpty()
                            val parsedKf = KeyframeEngine.parseKeyframesTxt(content)
                            withContext(Dispatchers.Main) {
                                if (parsedKf.isNotEmpty()) {
                                    activeProject = activeProject?.copy(keyframesUs = parsedKf)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Dimuat ${parsedKf.size} titik keyframe ungu",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    formatErrorMessage = "File keyframe (.txt/.log) tidak berisi daftar frame/waktu yang valid."
                                }
                            }
                        } catch (t: Throwable) {
                            withContext(Dispatchers.Main) {
                                formatErrorMessage = "Gagal membaca file keyframe: ${t.localizedMessage}"
                            }
                        }
                    }
                }

                val exportAssLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain"),
                ) { targetUri: Uri? ->
                    val contentToWrite = pendingAssExportContent
                    if (targetUri != null && contentToWrite.isNotEmpty()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            runCatching {
                                contentResolver.openOutputStream(targetUri)?.use { out ->
                                    out.write(contentToWrite.toByteArray(Charsets.UTF_8))
                                    out.flush()
                                }
                            }.onSuccess {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Berhasil menyimpan skrip .ASS",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
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
                            subtitleImportVersion = 0
                            activeProject = clickedProject
                        },
                        onCreateNewProject = { newZeroStateProject ->
                            selectedVideoUri = null
                            selectedAudioUri = null
                            subtitleImportVersion = 0
                            activeProject = newZeroStateProject
                        },
                    )
                } else {
                    BackHandler {
                        activeProject = null
                    }

                    EditorScreen(
                        project = currentProject,
                        subtitleImportVersion = subtitleImportVersion,
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
