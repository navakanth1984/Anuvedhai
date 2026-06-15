package com.example.ui

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Size
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.TranslationTurn
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

val LanguageOptions = listOf(
    LanguageOption("English", "en-IN"),
    LanguageOption("Hindi", "hi-IN"),
    LanguageOption("Bengali", "bn-IN"),
    LanguageOption("Tamil", "ta-IN"),
    LanguageOption("Telugu", "te-IN"),
    LanguageOption("Kannada", "kn-IN"),
    LanguageOption("Malayalam", "ml-IN"),
    LanguageOption("Marathi", "mr-IN"),
    LanguageOption("Gujarati", "gu-IN"),
    LanguageOption("Punjabi", "pa-IN")
)

val SourceLanguageOptions = listOf(
    LanguageOption("Auto-Detect", "auto")
) + LanguageOptions

private fun detectSentiment(text: String): Pair<String, Color> {
    val t = text.lowercase()
    return when {
        t.contains("urgent") || t.contains("immediately") || t.contains("asap") || t.contains("quick") -> "URGENT ⚡" to Color(0xFFE53935)
        t.contains("thank") || t.contains("great") || t.contains("happy") || t.contains("good") || t.contains("love") || t.contains("nice") -> "POSITIVE 😊" to Color(0xFF4CAF50)
        t.contains("lost") || t.contains("where") || t.contains("help") || t.contains("find") || t.contains("can you") -> "QUERY ❓" to Color(0xFFFF9800)
        else -> "INFORMATIONAL ℹ" to Color(0xFF2196F3)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(viewModel: TranslationViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val turns by viewModel.turns.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activeConvId by viewModel.activeConversationId.collectAsState()

    val sourceLang by viewModel.sourceLanguage.collectAsState()
    val targetLang by viewModel.targetLanguage.collectAsState()
    val useSarvam by viewModel.useSarvam.collectAsState()
    val sarvamApiKey by viewModel.sarvamApiKey.collectAsState()
    val darkThemeConfig by viewModel.darkThemeConfig.collectAsState()
    val ttsVolume by viewModel.ttsVolume.collectAsState()
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()

    val typedText by viewModel.typedText.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val activePlayingTurnId by viewModel.activePlayingTurnId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showEngineSettings by remember { mutableStateOf(false) }
    var showHistSheet by remember { mutableStateOf(false) }

    // Call translation interface simulator states
    var activeTabMode by remember { mutableStateOf("dialogue") } // "dialogue", "call_translator", or "mascot"
    var selectedCallPlatform by remember { mutableStateOf("WhatsApp") }
    var isCallActive by remember { mutableStateOf(false) }
    var isCallVideoEnabled by remember { mutableStateOf(true) }
    var isOverlayEnabled by remember { mutableStateOf(false) }
    var simulatedSpeechText by remember { mutableStateOf("") }
    var callPartnerLanguage by remember { mutableStateOf(LanguageOptions[1]) } // Default to Hindi
    var testPhraseText by remember { mutableStateOf("") }
    var myReplyText by remember { mutableStateOf("") }

    // Media Intent Helpers
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let { uri ->
                val cachedFile = copyUriToCacheFile(context, uri, "camera", ".jpg")
                if (cachedFile != null) {
                    viewModel.submitImageTranslation(cachedFile)
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val cachedFile = copyUriToCacheFile(context, uri, "gallery_ocr", ".jpg")
            if (cachedFile != null) {
                viewModel.submitImageTranslation(cachedFile)
            }
        }
    }

    val videoPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val cachedFile = copyUriToCacheFile(context, uri, "video_clip", ".mp4")
            if (cachedFile != null) {
                viewModel.submitVideoTranslation(cachedFile)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleRecording()
        } else {
            Toast.makeText(context, "Microphone recording permission is required to translate speech.", Toast.LENGTH_LONG).show()
        }
    }

    fun handleMicClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission || viewModel.isRecording.value) {
            viewModel.toggleRecording()
        } else {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun initiateCameraSnap() {
        try {
            val imageFile = File(context.cacheDir, "camera_snap_${System.currentTimeMillis()}.jpg")
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, imageFile)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("TranslationScreen", "Camera launch error", e)
            Toast.makeText(context, "Failed to launch device camera: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "AnuVedhai",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.darkThemeConfig.value = when (darkThemeConfig) {
                                "system" -> "light"
                                "light" -> "dark"
                                else -> "system"
                            }
                        },
                        modifier = Modifier.testTag("theme_shortcut_button")
                    ) {
                        val icon = when (darkThemeConfig) {
                            "light" -> Icons.Filled.WbSunny
                            "dark" -> Icons.Filled.NightsStay
                            else -> Icons.Filled.Contrast
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Theme: $darkThemeConfig"
                        )
                    }
                    IconButton(
                        onClick = { showHistSheet = true },
                        modifier = Modifier.testTag("history_button")
                    ) {
                        Icon(imageVector = Icons.Filled.History, contentDescription = "Interpreter Sessions")
                    }
                    IconButton(
                        onClick = { showEngineSettings = !showEngineSettings },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = if (showEngineSettings) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Active Engines",
                            tint = if (showEngineSettings) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Elegant linear progress indicator at the top for real-time API requests
            AnimatedVisibility(
                visible = isTranslating,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).testTag("top_api_progress_bar"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                )
            }

            // Error Display Banner
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                errorMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.errorMessage.value = null },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss error message",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Engine Configuration panel
            AnimatedVisibility(visible = showEngineSettings) {
                Card(
                    shape = RoundedCornerShape(0.dp, 0.dp, 16.dp, 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Engine Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Use Sarvam AI Engine",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Optimized, localized text/ASR for Indian languages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useSarvam,
                                onCheckedChange = { viewModel.useSarvam.value = it },
                                modifier = Modifier.testTag("sarvam_switch")
                            )
                        }

                        if (useSarvam) {
                            OutlinedTextField(
                                value = sarvamApiKey,
                                onValueChange = { viewModel.sarvamApiKey.value = it },
                                label = { Text("Sarvam AI Subscription Key") },
                                placeholder = { Text("Enter your paid Sarvam AI Key") },
                                singleLine = true,
                                trailingIcon = {
                                    if (sarvamApiKey.isNotBlank()) {
                                        IconButton(onClick = { viewModel.sarvamApiKey.value = "" }) {
                                            Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sarvam_api_key_field")
                            )
                            Text(
                                text = "Note: If vacant or invalid, the app falls back smoothly to built-in Gemini.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )

                            // Sarvam TTS options
                            Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            Text(
                                text = "Sarvam TTS Voice Gender",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val ttsGender by viewModel.sarvamTtsSpeakerGender.collectAsState()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { viewModel.sarvamTtsSpeakerGender.value = "Male" }
                                ) {
                                    RadioButton(
                                        selected = ttsGender == "Male",
                                        onClick = { viewModel.sarvamTtsSpeakerGender.value = "Male" },
                                        modifier = Modifier.testTag("gender_male")
                                    )
                                    Text(text = "Male Voice", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { viewModel.sarvamTtsSpeakerGender.value = "Female" }
                                ) {
                                    RadioButton(
                                        selected = ttsGender == "Female",
                                        onClick = { viewModel.sarvamTtsSpeakerGender.value = "Female" },
                                        modifier = Modifier.testTag("gender_female")
                                    )
                                    Text(text = "Female Voice", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("gemini_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Powered by built-in Gemini Flash AI with Context History preservation.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        // Global TTS/Auto-play settings
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Play Translations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Automatically speak output after translating",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val autoPlay by viewModel.autoPlayTts.collectAsState()
                            Switch(
                                checked = autoPlay,
                                onCheckedChange = { viewModel.autoPlayTts.value = it },
                                modifier = Modifier.testTag("autoplay_switch")
                            )
                        }

                        // Audio TTS Volume Slider
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = "Audio Playback Volume",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tts_volume_panel"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (ttsVolume == 0.0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                contentDescription = "TTS Volume Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Slider(
                                value = ttsVolume,
                                onValueChange = { viewModel.ttsVolume.value = it },
                                valueRange = 0.0f..1.0f,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tts_volume_slider")
                            )
                            Text(
                                text = "${(ttsVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .width(36.dp)
                                    .testTag("tts_volume_percentage")
                            )
                        }

                        // Audio TTS Playback Speed Slider
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = "Playback Speed Rate",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tts_speed_panel"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = "TTS Playback Speed Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Slider(
                                value = ttsSpeed,
                                onValueChange = { viewModel.ttsSpeed.value = it },
                                valueRange = 0.5f..2.0f,
                                steps = 5,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tts_speed_slider")
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.2fx", ttsSpeed),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .width(36.dp)
                                    .testTag("tts_speed_percentage")
                            )
                        }

                        // App Theme Settings
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = "App Theme Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { viewModel.darkThemeConfig.value = "system" }
                            ) {
                                RadioButton(
                                    selected = darkThemeConfig == "system",
                                    onClick = { viewModel.darkThemeConfig.value = "system" },
                                    modifier = Modifier.testTag("theme_system")
                                )
                                Text(text = "System", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { viewModel.darkThemeConfig.value = "light" }
                            ) {
                                RadioButton(
                                    selected = darkThemeConfig == "light",
                                    onClick = { viewModel.darkThemeConfig.value = "light" },
                                    modifier = Modifier.testTag("theme_light")
                                )
                                Text(text = "Light", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { viewModel.darkThemeConfig.value = "dark" }
                            ) {
                                RadioButton(
                                    selected = darkThemeConfig == "dark",
                                    onClick = { viewModel.darkThemeConfig.value = "dark" },
                                    modifier = Modifier.testTag("theme_dark")
                                )
                                Text(text = "Dark", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        // Keyboard Shortcuts Info
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = "Global Keyboard Shortcuts",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().testTag("keyboard_shortcuts_panel")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Toggle Voice Recording",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Ctrl + R  or  Alt + R",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("shortcut_record_hint")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Clear Chat History",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Ctrl + L  or  Alt + C",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("shortcut_clear_hint")
                                )
                            }
                        }
                    }
                }
            }

            // Language select panel (Source <-> Target)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Source Select Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                                .padding(vertical = 8.dp, horizontal = 12.dp)
                                .testTag("source_language_selector"),
                            color = Color.Transparent
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "FROM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = sourceLang.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = expanded, 
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.testTag("source_language_dropdown")
                        ) {
                            SourceLanguageOptions.forEach { opt ->
                                val isSelected = opt.code == sourceLang.code
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = opt.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        ) 
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.sourceLanguage.value = opt
                                        expanded = false
                                        Toast.makeText(context, "Source language changed to ${opt.displayName}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("source_lang_option_${opt.code}")
                                )
                            }
                        }
                    }

                    // Exchange Button
                    IconButton(
                        onClick = { viewModel.swapLanguages() },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .size(36.dp)
                            .testTag("swap_languages_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "Swap Languages",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Target Select Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                                .padding(vertical = 8.dp, horizontal = 12.dp)
                                .testTag("target_language_selector"),
                            color = Color.Transparent
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "TO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = targetLang.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = expanded, 
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.testTag("target_language_dropdown")
                        ) {
                            LanguageOptions.forEach { opt ->
                                val isSelected = opt.code == targetLang.code
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = opt.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        ) 
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.targetLanguage.value = opt
                                        expanded = false
                                        Toast.makeText(context, "Target language changed to ${opt.displayName}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("target_lang_option_${opt.code}")
                                )
                            }
                        }
                    }
                }
            }

            // --- MODE SWITCHER PILLS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { activeTabMode = "dialogue" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTabMode == "dialogue") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (activeTabMode == "dialogue") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f).testTag("dialogue_mode_tab"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chat", maxLines = 1, fontSize = 11.sp)
                }

                Button(
                    onClick = { activeTabMode = "call_translator" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTabMode == "call_translator") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (activeTabMode == "call_translator") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1.3f).testTag("call_assistant_mode_tab"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(imageVector = Icons.Filled.PhoneInTalk, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Live Call", maxLines = 1, fontSize = 11.sp)
                }

                Button(
                    onClick = { activeTabMode = "mascot" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTabMode == "mascot") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (activeTabMode == "mascot") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1.2f).testTag("mascot_mode_tab"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Vedhai", maxLines = 1, fontSize = 11.sp)
                }
            }

            if (activeTabMode == "dialogue") {
                // Interpreter conversation Timeline (Chat Flow)
            var searchQuery by remember { mutableStateOf("") }
            val filteredTurns = remember(turns, searchQuery) {
                if (searchQuery.isBlank()) {
                    turns
                } else {
                    turns.filter { turn ->
                        turn.originalContent.contains(searchQuery, ignoreCase = true) ||
                        turn.translatedText.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            val listState = rememberLazyListState()
            LaunchedEffect(turns.size) {
                if (turns.isNotEmpty()) {
                    listState.animateScrollToItem(turns.size - 1)
                }
            }

            // Elegant filter bar for translation history pairs
            if (turns.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("chat_search_bar"),
                    placeholder = { Text("Filter translation history...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("chat_search_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (turns.isEmpty()) {
                    // Friendly Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forum,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Start Translating with Context",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Type text, speak into your mic, or upload photos/videos. This dialogue remembers prior conversational context for flawless interpretation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (filteredTurns.isEmpty() && searchQuery.isNotEmpty()) {
                    // Empty filter results state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No matches found",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "We couldn't find any translation pairs matching \"$searchQuery\". Try checking the spelling or use different terms.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                    ) {
                        items(filteredTurns, key = { it.id }) { turn ->
                            TranslationTurnItem(
                                turn = turn,
                                isPlaying = isPlaying && activePlayingTurnId == turn.id,
                                onPlaybackToggle = { viewModel.playAudioTurn(turn) },
                                onCopyClick = {
                                    clipboardManager.setText(AnnotatedString(turn.translatedText))
                                    Toast.makeText(context, "Translation copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onCopyOriginalClick = {
                                    clipboardManager.setText(AnnotatedString(turn.originalContent))
                                    Toast.makeText(context, "Original copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onOriginalPlaybackToggle = { viewModel.playOriginalAudio(turn) }
                            )
                        }
                    }
                }

                // API thinking status
                if (isTranslating) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Translating near real-time...",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Real voice visualization when recording
            AnimatedVisibility(visible = isRecording) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val transition = rememberInfiniteTransition(label = "recording_pulse")
                            val scale by transition.animateFloat(
                                initialValue = 0.8f,
                                targetValue = 1.3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )
                            Surface(
                                modifier = Modifier
                                    .size(12.dp)
                                    .drawBehind { drawCircle(Color.Red, radius = size.minDimension / 2 * scale) },
                                color = Color.Red,
                                shape = CircleShape
                            ) {}
                            Text(
                                text = "SPEAK NOW",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Microphone is recording your speech in ${sourceLang.displayName}. Standard formatting translates automatically on stop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { viewModel.toggleRecording() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Stop & Translate")
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelRecording() }
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            // Bottom control pane (Text send, record, attach image/video)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    var selectedCategory by remember { mutableStateOf("Greetings 🤝") }
                    val categories = listOf("Greetings 🤝", "Travel ✈️", "Dining 🍽️", "Shopping 🛍️")
                    val phrases = mapOf(
                        "Greetings 🤝" to listOf(
                            "Hello, how are you?",
                            "Thank you very much",
                            "Nice to meet you",
                            "Could you please help me?",
                            "Good morning",
                            "Have a nice day"
                        ),
                        "Travel ✈️" to listOf(
                            "Where is the nearest railway station?",
                            "Can you show me on the map?",
                            "How do I get to the city center?",
                            "Is there a taxi stand nearby?",
                            "Where is the toilet?",
                            "I am lost, help me please"
                        ),
                        "Dining 🍽️" to listOf(
                            "Is this food very spicy?",
                            "Can I get some clean drinking water?",
                            "We need a table for two, please",
                            "Could you bring the menu?",
                            "Please bring us the bill",
                            "This food tastes delicious!"
                        ),
                        "Shopping 🛍️" to listOf(
                            "How much does this item cost?",
                            "Can you give me a discount?",
                            "Do you accept online payment/cards?",
                            "Where can I find a grocery store?",
                            "Do you have this in a different color?",
                            "No thanks, just looking around"
                        )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(vertical = 8.dp)
                            .testTag("quick_phrases_section")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Quick Phrases ⚡",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tap to instantly translate",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        // Category Chips
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("quick_phrases_categories_row"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(categories) { cat ->
                                val isSelected = cat == selectedCategory
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .clickable { selectedCategory = cat }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("category_chip_${cat.take(5)}")
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Phrases Chips
                        val currentPhrases = phrases[selectedCategory] ?: emptyList()
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_phrases_list_row"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(currentPhrases) { phrase ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .clickable { viewModel.translateQuickPhrase(phrase) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("phrase_chip_${phrase.take(10)}")
                                ) {
                                    Text(
                                        text = phrase,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var showAttachmentPopup by remember { mutableStateOf(false) }
 
                        // Attachment Button
                        IconButton(
                            onClick = { showAttachmentPopup = !showAttachmentPopup },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .size(44.dp)
                                .testTag("attachment_menu_button")
                        ) {
                            Icon(imageVector = Icons.Outlined.AttachFile, contentDescription = "Add media")
                        }

                    // Main Text Field
                    OutlinedTextField(
                        value = typedText,
                        onValueChange = {
                            if (it.length <= 3000) {
                                viewModel.typedText.value = it
                            }
                        },
                        placeholder = { Text("Type English/Indian text to translate...") },
                        maxLines = 4,
                        supportingText = {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${typedText.length} / 3000",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (typedText.length > 2700) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .testTag("char_counter")
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("typing_field"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { viewModel.submitTextTranslation() }),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )

                    // Action Trigger (Send or Microphone)
                    if (isTranslating) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                                .size(44.dp)
                                .testTag("sending_progress_container"),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).testTag("api_progress_spinner"),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else if (typedText.isNotBlank()) {
                        IconButton(
                            onClick = { viewModel.submitTextTranslation() },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .size(44.dp)
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = "Send text for translation",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { handleMicClick() },
                            modifier = Modifier
                                .background(
                                    if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .size(44.dp)
                                .testTag("mic_button")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isRecording) "Stop record" else "Record voice input",
                                tint = Color.White
                            )
                        }
                    }

                    // Nested Option Popups for camera/video captures
                    DropdownMenu(
                        expanded = showAttachmentPopup,
                        onDismissRequest = { showAttachmentPopup = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Take Camera Snap") },
                            leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                            onClick = {
                                showAttachmentPopup = false
                                initiateCameraSnap()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Upload Picture File") },
                            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                            onClick = {
                                showAttachmentPopup = false
                                galleryLauncher.launch("image/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Upload Video Clip") },
                            leadingIcon = { Icon(Icons.Filled.VideoFile, contentDescription = null) },
                            onClick = {
                                showAttachmentPopup = false
                                videoPickLauncher.launch("video/mp4")
                            }
                        )
                    }
                }
                }
            }
            } else if (activeTabMode == "call_translator") {
                Box(modifier = Modifier.weight(1f)) {
                    CallTranslatorWorkspace(viewModel = viewModel)
                }
            } else if (activeTabMode == "mascot") {
                Box(modifier = Modifier.weight(1f)) {
                    AnuMascotWorkspace(viewModel = viewModel)
                }
            }
        }
    }

    // Sessions Sidebar BottomSheet / Dialog
    if (showHistSheet) {
        AlertDialog(
            onDismissRequest = { showHistSheet = false },
            title = { Text("Interpreter Sessions") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    Text(
                        text = "Switch or clear active dialogue sessions. Turns maintain context database chains.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Button(
                        onClick = {
                            viewModel.startNewConversation()
                            showHistSheet = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_session_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("Start New Session")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(conversations) { conv ->
                            val isActive = conv.id == activeConvId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectConversation(conv.id)
                                        showHistSheet = false
                                    }
                                    .testTag("session_${conv.id}"),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = conv.title,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        val dateStr = SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault()).format(Date(conv.createdAt))
                                        Text(
                                            text = "Created: $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isActive) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteCurrentConversation()
                                            showHistSheet = false
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete Session", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistSheet = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearTurnsInCurrentConversation()
                        showHistSheet = false
                    },
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Text("Clear Current Progress", color = Color.Red)
                }
            }
        )
    }
}

@Composable
fun TranslationTurnItem(
    turn: TranslationTurn,
    isPlaying: Boolean,
    onPlaybackToggle: () -> Unit,
    onCopyClick: () -> Unit,
    onCopyOriginalClick: () -> Unit,
    onOriginalPlaybackToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("turn_${turn.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Input type badge, date and languages
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when (turn.inputType) {
                            "AUDIO" -> Icons.Filled.Mic
                            "IMAGE" -> Icons.Filled.PhotoCamera
                            "VIDEO" -> Icons.Filled.VideoFile
                            else -> Icons.Filled.Keyboard
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = turn.inputType,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (turn.durationMs > 0L) {
                        val isFromCache = turn.durationMs <= 45L && (turn.inputType == "TEXT" || turn.inputType == "AUDIO")
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isFromCache) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.testTag("latency_badge_${turn.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFromCache) Icons.Filled.Bolt else Icons.Filled.AccessTime,
                                    contentDescription = if (isFromCache) "Cached" else "Translation latency",
                                    modifier = Modifier.size(10.dp),
                                    tint = if (isFromCache) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (isFromCache) "Local Cache" else "${turn.durationMs} ms",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFromCache) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "${turn.sourceLanguageName} → ${turn.targetLanguageName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Multimedia item preview
            if (turn.mediaPath != null && (turn.inputType == "IMAGE" || turn.inputType == "VIDEO")) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = turn.mediaPath,
                        contentDescription = "Visual reference snapshot",
                        modifier = Modifier.fillMaxSize()
                    )
                    if (turn.inputType == "VIDEO") {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleFilled,
                            contentDescription = "Video Clip",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }

            // Original content transcription block
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SOURCE (${turn.sourceLanguageName})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = turn.originalContent.ifBlank { "Unprocessed content..." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (turn.mediaPath != null && turn.inputType == "AUDIO") {
                        IconButton(
                            onClick = onOriginalPlaybackToggle,
                            modifier = Modifier.testTag("playback_original_button_${turn.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play original audio",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onCopyOriginalClick,
                        modifier = Modifier.testTag("copy_original_button_${turn.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Original Text",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

            // Translated output block
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRANSLATION (${turn.targetLanguageName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SentimentBadge(turn.sentiment)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = turn.translatedText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPlaybackToggle,
                        modifier = Modifier.testTag("playback_button_${turn.id}")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (isPlaying) "Stop audio" else "Speak translation",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onCopyClick,
                        modifier = Modifier.testTag("copy_button_${turn.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Translation",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// Helper to copy a system content Uri into a localized Cache file
private fun copyUriToCacheFile(context: Context, uri: Uri, prefix: String, suffix: String): File? {
    return try {
        val stream: InputStream? = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}$suffix")
        file.outputStream().use { out ->
            stream?.copyTo(out)
        }
        file
    } catch (e: Exception) {
        Log.e("UriToFile", "Failure copying content URI stream to physical file context", e)
        null
    }
}

@Composable
fun SentimentBadge(sentiment: String?) {
    if (sentiment.isNullOrBlank()) return
    val (emoji, label, color) = when (sentiment.lowercase()) {
        "happy" -> Triple("😊", "Happy", MaterialTheme.colorScheme.primary)
        "serious" -> Triple("😐", "Serious", MaterialTheme.colorScheme.secondary)
        "urgent" -> Triple("🚨", "Urgent", MaterialTheme.colorScheme.error)
        "peaceful" -> Triple("😌", "Peaceful", MaterialTheme.colorScheme.tertiary)
        "sad" -> Triple("😢", "Sad", MaterialTheme.colorScheme.outline)
        "angry" -> Triple("😠", "Angry", MaterialTheme.colorScheme.error)
        else -> Triple("😐", "Neutral", MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f),
        modifier = Modifier
            .padding(start = 8.dp)
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .testTag("sentiment_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = emoji, fontSize = 12.sp)
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun CallTranslatorWorkspace(viewModel: TranslationViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val turns by viewModel.turns.collectAsState()

    // Local Simulation State
    var isExtensionInjected by remember { mutableStateOf(true) }
    var selectedPlatform by remember { mutableStateOf("WhatsApp Web") }
    var partnerLanguage by remember { mutableStateOf(LanguageOptions[1]) } // Default to Hindi
    var customSpeechInput by remember { mutableStateOf("") }
    
    // Subtitle Customizations
    var overlayTheme by remember { mutableStateOf("Translucent Obsidian") } // "Translucent Obsidian", "Amber Glow", "Neon Cyan", "Polar Ice"
    var subtitleFontSize by remember { mutableStateOf("Medium") } // "Small", "Medium", "Large", "Gigantic"
    var overlayPosition by remember { mutableStateOf("Bottom Overlay") } // "Top Subtitle", "Bottom Overlay", "Floating PiP"
    
    // Last Translation simulated displays
    var simulatedOriginalText by remember { mutableStateOf("") }
    var simulatedTranslatedText by remember { mutableStateOf("") }
    var isSimulatingTranslation by remember { mutableStateOf(false) }

    // Content Script Code Segment Toggle
    var showDeveloperCodePayload by remember { mutableStateOf(false) }

    // Animation values for Stream Detection Meter
    val infiniteTransition = rememberInfiniteTransition(label = "audio_bars")
    val animPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.2f at 0
                0.8f at 250
                0.4f at 500
                1.0f at 750
                0.2f at 1000
            },
            repeatMode = RepeatMode.Reverse
        ),
        label = "audio_bars_pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("call_translator_workspace"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // GENERAL INSTRUCTIONS HERO CARD
        Card(
            modifier = Modifier.fillMaxWidth().testTag("extension_hero_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = "Companion Overlay",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Browser Extension Overlay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "This companion workspace simulates our Chrome/Firefox Extension API. The injected script intercepts live audio streams from communication portals, decrypts them via low-latency Sarvam/Gemini engine APIs, and projects dynamic floating translation overlays onto your call screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
        }

        // BROWSER STREAM DETECTION PANEL
        Card(
            modifier = Modifier.fillMaxWidth().testTag("stream_detection_panel"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hook state switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Audio Stream Grabbing Hook",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isExtensionInjected) "🟢 Extension connected to target tab" else "🔴 Hook disconnected (idle)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isExtensionInjected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = isExtensionInjected,
                        onCheckedChange = { isExtensionInjected = it },
                        modifier = Modifier.testTag("extension_toggle_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                // Target platforms selector
                Text(
                    text = "Active Target Platform Connectors",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                val platforms = listOf(
                    Triple("WhatsApp Web", Icons.Filled.Chat, Color(0xFF25D366)),
                    Triple("Google Meet", Icons.Filled.VideoCall, Color(0xFF00AC47)),
                    Triple("Teams Web", Icons.Filled.Groups, Color(0xFF4653B7)),
                    Triple("Zoom Web", Icons.Filled.Videocam, Color(0xFF2D8CFF))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    platforms.forEach { (platName, platIcon, platColor) ->
                        val isSelected = selectedPlatform == platName
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedPlatform = platName
                                    Toast.makeText(context, "$platName connector selected", Toast.LENGTH_SHORT).show()
                                }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) platColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .testTag("platform_pill_${platName.replace(" ", "_")}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) platColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = platIcon,
                                    contentDescription = platName,
                                    tint = if (isSelected) platColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = platName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (isExtensionInjected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    // decibel scale
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cast,
                            contentDescription = "Active Stream Indicator",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Web-Audio Extraction Stream",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Decoding 48kHz audio buffers from matching background pipeline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Web Stream visualizer bars
                        Canvas(
                            modifier = Modifier
                                .width(70.dp)
                                .height(22.dp)
                                .testTag("stream_audio_meter")
                        ) {
                            val barCount = 10
                            val barSpacing = 4f
                            val barWidth = (size.width - (barSpacing * (barCount - 1))) / barCount
                            for (i in 0 until barCount) {
                                // generate random-looking heights modulated by animator
                                val waveFactor = java.lang.Math.abs(java.lang.Math.sin(i * 0.7))
                                val barHeight = size.height * (0.1f + waveFactor * 0.9f * animPulse)
                                drawRoundRect(
                                    color = Color(0xFF00AC47),
                                    topLeft = Offset(i * (barWidth + barSpacing), (size.height - barHeight.toFloat()) / 2f),
                                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight.toFloat()),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // BROWSER VOICE CORPUS SIMULATOR PANEL
        Card(
            modifier = Modifier.fillMaxWidth().testTag("voice_corpus_simulator_panel"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Live Call Voice Simulator",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Simulate oral voice exchanges captured by your browser tab to experience downstream overlays in real-time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Partner language select row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Call Partner's Language:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    var expandedPartnerLang by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { expandedPartnerLang = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp).testTag("partner_lang_selector_btn")
                        ) {
                            Text(partnerLanguage.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = expandedPartnerLang,
                            onDismissRequest = { expandedPartnerLang = false }
                        ) {
                            LanguageOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.displayName) },
                                    onClick = {
                                        partnerLanguage = opt
                                        expandedPartnerLang = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                // Popular simulated phrases
                Text(
                    text = "Tap to simulate inbound web streams:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val samplePhrases = when (partnerLanguage.displayName) {
                    "Hindi" -> listOf(
                        "नमस्ते! क्या आप मुझे सुन पा रहे हैं? बैठक शुरू हो चुकी है।" to "Hello! Can you hear me? The meeting has started.",
                        "यह अत्यंत महत्वपूर्ण है, हमें इस कोड बग को अभी ठीक करना होगा।" to "This is extremely urgent, we need to fix this code bug right now."
                    )
                    "Tamil" -> listOf(
                        "வணக்கம், இந்த திட்டம் நாளை முடிவடைய வேண்டும்." to "Hello, this project must be completed tomorrow.",
                        "தயவுசெய்து ஆவணம் சரிபார்க்கவும், நன்றி!" to "Please check the document, thank you!"
                    )
                    "Telugu" -> listOf(
                        "నమస్కారం, దయచేసి వివరాలను పంపగలరా?" to "Hello, could you please send the details?",
                        "ఇది చాలా అవసరమైన సమస్య, వెంటనే సహాయం చేయండి." to "This is a very urgent issue, please help immediately."
                    )
                    "Kannada" -> listOf(
                        "ನಮಸ್ಕಾರ, ನಮಗೆ ಸಹಾಯ ಬೇಕು, ದಯವಿಟ್ಟು ಬೇಗ ಬನ್ನಿ." to "Hello, we need help, please come quickly."
                    )
                    else -> listOf(
                        "Hola amigó! ¿Cómo de va todo allá?" to "Hello friend! How is everything going over there?",
                        "S'il vous plaît, réactivez votre micro, nous ne vous entendons pas." to "Please unmute your microphone, we can't hear you."
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(samplePhrases) { (nativeText, engTrans) ->
                        SuggestionChip(
                            onClick = {
                                customSpeechInput = nativeText
                                Toast.makeText(context, "Copied simulated speech buffer", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(nativeText, maxLines = 1, fontSize = 11.sp) },
                            modifier = Modifier.testTag("phrase_chip_${nativeText.hashCode()}")
                        )
                    }
                }

                // Simulated text field input
                OutlinedTextField(
                    value = customSpeechInput,
                    onValueChange = { customSpeechInput = it },
                    placeholder = { Text("Enter simulated call speech in ${partnerLanguage.displayName}...") },
                    modifier = Modifier.fillMaxWidth().testTag("col_sim_speech_input"),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Translate Partner spoken
                    Button(
                        onClick = {
                            if (customSpeechInput.isBlank()) {
                                Toast.makeText(context, "Please enter or tap speech to translate", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!isExtensionInjected) {
                                Toast.makeText(context, "Turn on 'Audio Stream Grabbing Hook' to receive streaming data", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            isSimulatingTranslation = true
                            simulatedOriginalText = customSpeechInput
                            
                            viewModel.simulateCallTranslation(
                                textInput = customSpeechInput,
                                partnerLang = partnerLanguage,
                                ourLang = viewModel.targetLanguage.value,
                                isPartnerSpeaking = true
                            )
                            // Simulate completion response
                            simulatedTranslatedText = "Translating stream..."
                        },
                        modifier = Modifier.weight(1f).testTag("sim_partner_speak_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Filled.PhoneInTalk, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Partner Spoke", fontSize = 12.sp, maxLines = 1)
                    }

                    // Translate we spoken
                    Button(
                        onClick = {
                            if (customSpeechInput.isBlank()) {
                                Toast.makeText(context, "Please enter text you want to reply with", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!isExtensionInjected) {
                                Toast.makeText(context, "Turn on 'Audio Stream Grabbing Hook' to execute streams", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            isSimulatingTranslation = true
                            simulatedOriginalText = customSpeechInput
                            
                            viewModel.simulateCallTranslation(
                                textInput = customSpeechInput,
                                partnerLang = partnerLanguage,
                                ourLang = viewModel.targetLanguage.value,
                                isPartnerSpeaking = false
                            )
                            simulatedTranslatedText = "Translating outstream..."
                        },
                        modifier = Modifier.weight(1f).testTag("sim_me_speak_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(imageVector = Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("My Reply", fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }

        // DYNAMIC FLOATING SUBTITLE OVERLAY INTEGRATION
        Card(
            modifier = Modifier.fillMaxWidth().testTag("overlay_setup_panel"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Floating HTML Subtitle Overlay Mockup",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure visual parameters of the generated Web Overlay. Changes adapt CSS custom attributes synchronously.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Subtitle Styles Settings grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Position Select
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Overlay Alignment", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        val positions = listOf("Top Subtitle", "Bottom Overlay", "Floating PiP")
                        var expPos by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { expPos = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth().height(34.dp).testTag("overlay_pos_opt")
                            ) {
                                Text(overlayPosition, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = expPos, onDismissRequest = { expPos = false }) {
                                positions.forEach { pos ->
                                    DropdownMenuItem(text = { Text(pos) }, onClick = { overlayPosition = pos; expPos = false })
                                }
                            }
                        }
                    }

                    // Theme Contrast CSS class select
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CSS Theme Preset", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        val themes = listOf("Translucent Obsidian", "Amber Glow", "Neon Cyan", "Polar Ice")
                        var expTh by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { expTh = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth().height(34.dp).testTag("overlay_theme_opt")
                            ) {
                                Text(overlayTheme, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = expTh, onDismissRequest = { expTh = false }) {
                                themes.forEach { th ->
                                    DropdownMenuItem(text = { Text(th) }, onClick = { overlayTheme = th; expTh = false })
                                }
                            }
                        }
                    }

                    // Font Scaling sizes
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Font Scale", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        val sizes = listOf("Small", "Medium", "Large", "Gigantic")
                        var expSiz by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { expSiz = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth().height(34.dp).testTag("overlay_font_opt")
                            ) {
                                Text(subtitleFontSize, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = expSiz, onDismissRequest = { expSiz = false }) {
                                sizes.forEach { sz ->
                                    DropdownMenuItem(text = { Text(sz) }, onClick = { subtitleFontSize = sz; expSiz = false })
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                // Watch latest simulated results inside turns list
                LaunchedEffect(turns.size) {
                    if (turns.isNotEmpty() && isSimulatingTranslation) {
                        val lastTurn = turns.first()
                        simulatedOriginalText = lastTurn.originalContent
                        simulatedTranslatedText = lastTurn.translatedText
                    }
                }

                // THE EXPERIMENT OVERLAY SCREEN DRAWER DISPLAY REPLICA
                Text(
                    text = "Live Injected Browser Overlay Viewport mockup:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )

                // The visual Overlay Replica Card
                val overlayBgColor = when (overlayTheme) {
                    "Amber Glow" -> Color(0xFF1E1400)
                    "Neon Cyan" -> Color(0xFF001F25)
                    "Polar Ice" -> Color(0xFFF0F4F8)
                    else -> Color(0xFF18181A) // Obsidian
                }
                val overlayBorderColor = when (overlayTheme) {
                    "Amber Glow" -> Color(0xFFFF9800)
                    "Neon Cyan" -> Color(0xFF00BCD4)
                    "Polar Ice" -> Color(0xFF90A4AE)
                    else -> Color(0xFF333336)
                }
                val overlayTextColor = if (overlayTheme == "Polar Ice") Color(0xFF263238) else Color.White
                val overlaySubtitleHighlightColor = when (overlayTheme) {
                    "Amber Glow" -> Color(0xFFFFD54F)
                    "Neon Cyan" -> Color(0xFF80DEEA)
                    "Polar Ice" -> Color(0xFF00ACC1)
                    else -> MaterialTheme.colorScheme.primary
                }

                val customFontSize = when (subtitleFontSize) {
                    "Small" -> 11.sp
                    "Large" -> 16.sp
                    "Gigantic" -> 20.sp
                    else -> 13.sp // Medium
                }

                val alignmentBias = when (overlayPosition) {
                    "Top Subtitle" -> Alignment.TopCenter
                    "Floating PiP" -> Alignment.CenterEnd
                    else -> Alignment.BottomCenter
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .background(Color(0xFF212124), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF333336), RoundedCornerShape(12.dp))
                        .testTag("visual_overlay_viewport"),
                    contentAlignment = Alignment.Center
                ) {
                    // Simulated Web Meeting screen capture background frame
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Meet top indicator
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🌐 $selectedPlatform Frame (Simulated Context)",
                                fontSize = 10.sp,
                                color = Color.LightGray,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.Red, CircleShape)
                                )
                                Text("REC FEED", fontSize = 9.sp, color = Color.Red, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Web Camera Tiles mockup
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // User Tile
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFF36363B), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(28.dp))
                                    Text("You (Speaking En)", fontSize = 9.sp, color = Color.Gray)
                                }
                            }

                            // Partner Tile
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFF36363B), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Hearing, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(28.dp))
                                    Text("${partnerLanguage.displayName} contact", fontSize = 9.sp, color = Color.Gray)
                                    if (isSimulatingTranslation) {
                                        Text("Active speaking stream...", fontSize = 8.sp, color = Color(0xFF00AC47), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Bottom Meeting Bar Mockup
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF16161A))
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp).padding(horizontal = 4.dp))
                            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp).padding(horizontal = 4.dp))
                            Icon(Icons.Filled.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp).padding(horizontal = 4.dp))
                            Box(
                                modifier = Modifier
                                    .size(16.dp, 10.dp)
                                    .background(Color.Red, RoundedCornerShape(2.dp))
                            )
                        }
                    }

                    // THE FLOATING SUBTITLE COMPONENT INJECTED BY CHROME EXTENSION
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 24.dp),
                        contentAlignment = alignmentBias
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, overlayBorderColor, RoundedCornerShape(8.dp))
                                .testTag("injected_html_overlay"),
                            shape = RoundedCornerShape(8.dp),
                            color = overlayBgColor.copy(alpha = 0.9f),
                            tonalElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(
                                            imageVector = Icons.Filled.Layers,
                                            contentDescription = null,
                                            tint = overlaySubtitleHighlightColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Sarvam Overlay Active [Chrome Native WebSocket]",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = overlayTextColor.copy(alpha = 0.7f)
                                        )
                                    }

                                    if (simulatedOriginalText.isNotEmpty()) {
                                        val (sentimentLabel, sentimentColor) = detectSentiment(simulatedOriginalText)
                                        Surface(
                                            color = sentimentColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, sentimentColor.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = sentimentLabel,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = sentimentColor,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (simulatedOriginalText.isEmpty()) {
                                    Text(
                                        text = "Awaiting live web communication audio stream. Click 'Partner Spoke' or select a test phrase above to simulate.",
                                        fontSize = 11.sp,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = overlayTextColor.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    )
                                } else {
                                    Text(
                                        text = "$simulatedOriginalText",
                                        fontSize = (customFontSize.value - 2).sp,
                                        color = overlayTextColor.copy(alpha = 0.7f),
                                        lineHeight = (customFontSize.value + 2).sp
                                    )
                                    Text(
                                        text = simulatedTranslatedText,
                                        fontSize = customFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = overlaySubtitleHighlightColor,
                                        lineHeight = (customFontSize.value + 4).sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // CHROME EXTENSION CONTENT-SCRIPT INJECTOR UTILITIES
        Card(
            modifier = Modifier.fillMaxWidth().testTag("extension_script_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Expand button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeveloperCodePayload = !showDeveloperCodePayload }
                        .testTag("extension_dev_toggle"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                text = "Show Chromium Extension Injection Code",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Copy verified content-script JavaScript payload to use in Tampermonkey or custom extension",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showDeveloperCodePayload) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                        contentDescription = "Expand payload"
                    )
                }

                if (showDeveloperCodePayload) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                    val manifestPayload = """
{
  "name": "Sarvam Anuvad Live Overlay",
  "version": "1.0",
  "manifest_version": 3,
  "permissions": ["tabCapture", "activeTab"],
  "content_scripts": [{
    "matches": [
      "*://web.whatsapp.com/*",
      "*://meet.google.com/*",
      "*://teams.live.com/*"
    ],
    "js": ["content.js"]
  }]
}
                    """.trimIndent()

                    val jsPayloadScript = """
// content.js - Injected WebRTC Audio Capture Node
(() => {
  console.log("🟢 [Sarvam Anuvad API] Browser Connection Injected OK.");
  const audioContext = new (window.AudioContext || window.webkitAudioContext)();
  const destStream = audioContext.createMediaStreamDestination();
  
  const injectAudioTag = (tag) => {
    try {
      const source = audioContext.createMediaElementSource(tag);
      source.connect(audioContext.destination);
      source.connect(destStream);
      console.log("🎯 Connected translation pipe to audio target:", tag);
    } catch(e) {}
  };

  // Dynamically monitor WebRTC audio tags for Google Meet or WhatsApp Web calls
  setInterval(() => {
    document.querySelectorAll("audio, video").forEach(injectAudioTag);
  }, 2000);
})();
                    """.trimIndent()

                    Text(text = "manifest.json Configuration:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    ) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                text = manifestPayload,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "content.js Content-Script Node:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(jsPayloadScript))
                                Toast.makeText(context, "JavaScript code copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(34.dp).testTag("copy_extension_js_btn")
                        ) {
                            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy Injection Payload", modifier = Modifier.size(16.dp))
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    ) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                text = jsPayloadScript,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        UniversalMobileVideoCallIntegrationSuite(viewModel = viewModel)
    }
}

@Composable
fun AnuMascotWorkspace(viewModel: TranslationViewModel) {
    val context = LocalContext.current
    val turns by viewModel.turns.collectAsState()
    
    var activeMascotStyle by remember { mutableStateOf("Mitthu: Cyber Parrot") }
    var currentSpeechBubbleText by remember { mutableStateOf("Namaste! I am Anu. Select an archetype above and tap me to receive translation wisdom.") }
    
    // Sliders
    var translationSpeed by remember { mutableStateOf(0.85f) }
    var emotionalScale by remember { mutableStateOf(0.7f) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_bouncing")
    val bounceY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bouncing_y"
    )
    
    val blinkScaleY by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                1.0f at 0
                1.0f at 2800
                0.05f at 2900
                1.0f at 3000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink_scale_y"
    )
    
    val popCultureSayings = mapOf(
        "Mitthu: Cyber Parrot" to listOf(
            "Anu says: 'Language is just a code, and I am the key to cracking it!'",
            "Mitthu says: 'Excellent choice! In ancient Indian stories, talking parrots like Mitthu were keepers of complex messages. Now, I speak fluent cyber!'",
            "Proverb: 'கற்றது கைம்மண் அளவு, கல்லாதது உலகளவு' - What we have learned is like a handful of sand; what we have yet to learn is like the entire universe.",
            "Saying: 'Language is the road map of a culture. It tells you where its people come from and where they are going.'",
            "Anu says: 'Bridging English, Hindi, Tamil, Kannada, and Telugu at light speed!'"
        ),
        "Lambodara: Ganesha Scribe" to listOf(
            "Scribe Ganesha says: 'A broken tusk is no obstacle for a master transcriber working under Vyasa's low-latency streaming terms!'",
            "Ganesha says: 'True translation is not just substituting words, but carrying the cosmic weight of the soul.'",
            "Wisdom: 'In ancient Indian culture, listening is called Shravanam - the first path of wisdom.'",
            "Ganesha says: 'My cyber visor processes 108 gigabits of conversational context per millisecond! Let us translate!'"
        ),
        "Hermes: Solar Envoy" to listOf(
            "Hermes says: 'My winged heels are powered by direct REST channels and advanced AI language engines!'",
            "World Culture: 'Translation is that which transforms everything so that nothing changes.'",
            "Saying: 'To have another language is to possess a second soul.'",
            "Hermes says: 'Warping messages across boundaries and dimensions! Hold on tight!'"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("anu_mascot_workspace"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // MAIN THEME BANNER
        Card(
            modifier = Modifier.fillMaxWidth().testTag("mascot_welcome_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "AnuVedhai Mascot",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "AnuVedhai Scribe Sagas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = "Welcome to AnuVedhai's companion hub! Our translation mascots are physical anchors representing ancient, classic, and folklore translators. Explore custom archetypes, trigger bilingual context wisdom, and observe interactive commentary on your chat logs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
        }

        // ARCHETYPE SELECTION HORIZONTAL GRID
        Text(
            text = "Select Dynamic Culture Mascot:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val archetypes = listOf(
                "Mitthu: Cyber Parrot" to Icons.Filled.Pets,
                "Lambodara: Ganesha Scribe" to Icons.Filled.Edit,
                "Hermes: Solar Envoy" to Icons.Filled.FlashOn
            )
            
            archetypes.forEach { (name, icon) ->
                val isSelected = activeMascotStyle == name
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            activeMascotStyle = name
                            currentSpeechBubbleText = popCultureSayings[name]?.random() ?: "Namaste!"
                        }
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("archetype_${name.replace(" ", "_")}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = name,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = name.substringBefore(":"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // MASCOT ANIMATED STAGE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Speech bubble card
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("mascot_speech_bubble")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forum,
                            contentDescription = "Speech Bubble",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = currentSpeechBubbleText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Custom mascot rendering in dynamic Canvas
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .offset(y = bounceY.dp)
                        .clickable {
                            currentSpeechBubbleText = popCultureSayings[activeMascotStyle]?.random() ?: "Namaste!"
                        }
                        .testTag("interactive_mascot_canvas"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val centerX = width / 2
                        val centerY = height / 2

                        when (activeMascotStyle) {
                            "Mitthu: Cyber Parrot" -> {
                                // Draw Cyber Parrot
                                // Head Body
                                drawCircle(
                                    color = Color(0xFF00C853),
                                    radius = 48.dp.toPx(),
                                    center = Offset(centerX, centerY + 10.dp.toPx())
                                )
                                // Belly Accent
                                drawCircle(
                                    color = Color(0xFFFFEB3B),
                                    radius = 24.dp.toPx(),
                                    center = Offset(centerX, centerY + 28.dp.toPx())
                                )
                                // Left Eye
                                drawCircle(
                                    color = Color.White,
                                    radius = 12.dp.toPx(),
                                    center = Offset(centerX - 16.dp.toPx(), centerY - 4.dp.toPx())
                                )
                                // Right Eye
                                drawCircle(
                                    color = Color.White,
                                    radius = 12.dp.toPx(),
                                    center = Offset(centerX + 16.dp.toPx(), centerY - 4.dp.toPx())
                                )
                                // Pupils with blink
                                drawOval(
                                    color = Color.Black,
                                    topLeft = Offset(centerX - 21.dp.toPx(), centerY - 8.dp.toPx() + (6.dp.toPx() * (1f - blinkScaleY))),
                                    size = Size(10.dp.toPx(), 8.dp.toPx() * blinkScaleY)
                                )
                                drawOval(
                                    color = Color.Black,
                                    topLeft = Offset(centerX + 11.dp.toPx(), centerY - 8.dp.toPx() + (6.dp.toPx() * (1f - blinkScaleY))),
                                    size = Size(10.dp.toPx(), 8.dp.toPx() * blinkScaleY)
                                )
                                // Beak
                                val beakPath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(centerX - 6.dp.toPx(), centerY + 4.dp.toPx())
                                    quadraticTo(centerX + 16.dp.toPx(), centerY + 14.dp.toPx(), centerX - 2.dp.toPx(), centerY + 26.dp.toPx())
                                    quadraticTo(centerX - 10.dp.toPx(), centerY + 14.dp.toPx(), centerX - 6.dp.toPx(), centerY + 4.dp.toPx())
                                }
                                drawPath(beakPath, color = Color(0xFFFF3D00))

                                // Headphones
                                drawArc(
                                    color = Color(0xFF00E5FF),
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 6.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    ),
                                    topLeft = Offset(centerX - 46.dp.toPx(), centerY - 40.dp.toPx()),
                                    size = Size(92.dp.toPx(), 90.dp.toPx())
                                )
                                drawRoundRect(
                                    color = Color(0xFF00E5FF),
                                    topLeft = Offset(centerX - 52.dp.toPx(), centerY - 8.dp.toPx()),
                                    size = Size(12.dp.toPx(), 28.dp.toPx()),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                                )
                                drawRoundRect(
                                    color = Color(0xFF00E5FF),
                                    topLeft = Offset(centerX + 40.dp.toPx(), centerY - 8.dp.toPx()),
                                    size = Size(12.dp.toPx(), 28.dp.toPx()),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                                )
                            }
                            "Lambodara: Ganesha Scribe" -> {
                                drawCircle(
                                    color = Color(0xFFFFB300),
                                    radius = 44.dp.toPx(),
                                    center = Offset(centerX, centerY + 8.dp.toPx())
                                )
                                drawCircle(
                                    color = Color(0xFFFFB300),
                                    radius = 22.dp.toPx(),
                                    center = Offset(centerX - 42.dp.toPx(), centerY)
                                )
                                drawCircle(
                                    color = Color(0xFFFFB300),
                                    radius = 22.dp.toPx(),
                                    center = Offset(centerX + 42.dp.toPx(), centerY)
                                )
                                drawCircle(
                                    color = Color(0xFFFF8A80),
                                    radius = 12.dp.toPx(),
                                    center = Offset(centerX - 42.dp.toPx(), centerY)
                                )
                                drawCircle(
                                    color = Color(0xFFFF8A80),
                                    radius = 12.dp.toPx(),
                                    center = Offset(centerX + 42.dp.toPx(), centerY)
                                )
                                drawRoundRect(
                                    color = Color(0xFFE040FB),
                                    topLeft = Offset(centerX - 34.dp.toPx(), centerY - 14.dp.toPx()),
                                    size = Size(68.dp.toPx(), 18.dp.toPx()),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(centerX - 24.dp.toPx(), centerY - 10.dp.toPx()),
                                    end = Offset(centerX - 10.dp.toPx(), centerY - 10.dp.toPx()),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                val trunkPath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(centerX - 6.dp.toPx(), centerY + 14.dp.toPx())
                                    quadraticTo(centerX - 16.dp.toPx(), centerY + 36.dp.toPx(), centerX + 10.dp.toPx(), centerY + 36.dp.toPx())
                                    quadraticTo(centerX + 16.dp.toPx(), centerY + 30.dp.toPx(), centerX + 4.dp.toPx(), centerY + 28.dp.toPx())
                                    quadraticTo(centerX - 4.dp.toPx(), centerY + 28.dp.toPx(), centerX - 2.dp.toPx(), centerY + 14.dp.toPx())
                                }
                                drawPath(trunkPath, color = Color(0xFFFFB300))
                                
                                drawLine(
                                    color = Color(0xFFFF3D00),
                                    start = Offset(centerX, centerY - 32.dp.toPx()),
                                    end = Offset(centerX, centerY - 18.dp.toPx()),
                                    strokeWidth = 4.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                            else -> {
                                drawCircle(
                                    color = Color(0xFFFF5722),
                                    radius = 42.dp.toPx(),
                                    center = Offset(centerX, centerY + 10.dp.toPx())
                                )
                                val leftWing = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(centerX - 35.dp.toPx(), centerY)
                                    quadraticTo(centerX - 65.dp.toPx(), centerY - 24.dp.toPx(), centerX - 55.dp.toPx(), centerY + 10.dp.toPx())
                                    quadraticTo(centerX - 35.dp.toPx(), centerY + 12.dp.toPx(), centerX - 35.dp.toPx(), centerY)
                                }
                                drawPath(leftWing, color = Color(0xFFFFD54F))
                                val rightWing = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(centerX + 35.dp.toPx(), centerY)
                                    quadraticTo(centerX + 65.dp.toPx(), centerY - 24.dp.toPx(), centerX + 55.dp.toPx(), centerY + 10.dp.toPx())
                                    quadraticTo(centerX + 35.dp.toPx(), centerY + 12.dp.toPx(), centerX + 35.dp.toPx(), centerY)
                                }
                                drawPath(rightWing, color = Color(0xFFFFD54F))

                                drawRoundRect(
                                    color = Color(0xFF69F0AE),
                                    topLeft = Offset(centerX - 24.dp.toPx(), centerY - 6.dp.toPx()),
                                    size = Size(48.dp.toPx(), 14.dp.toPx()),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "👇 Tap Anu to hear pop-cultural translation lore!",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // CHAT RECENT CORRESPONDENCE CARD
        Card(
            modifier = Modifier.fillMaxWidth().testTag("mascot_recent_turn_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last Synced Translation Stream",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "ACTIVE SYNC",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                val lastTurn = turns.firstOrNull()
                if (lastTurn == null) {
                    Text(
                        text = "No dialogues translated in current session yet! Go back to the 'Chat' or 'Live Call' tab to feed the AI context pipeline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Hearing, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Original (${lastTurn.sourceLanguageName}):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = lastTurn.originalContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Translation (${lastTurn.targetLanguageName}):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = lastTurn.translatedText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Custom mascot critique
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🦜",
                                fontSize = 18.sp
                            )
                            Column {
                                val mascotCritique = when {
                                    lastTurn.originalContent.contains("hello", ignoreCase = true) || lastTurn.originalContent.contains("namaste", ignoreCase = true) -> 
                                        "Anu says: 'What beautiful manners! A solid start to mutual understanding.'"
                                    lastTurn.translatedText.length > 50 -> 
                                        "Anu says: 'Complex wisdom demands a deep channel. The translation maintains beautiful sentence structural coherence!'"
                                    else -> 
                                        "Anu says: 'Fast, secure, and accurate! The translation bridges minds instantly!'"
                                }
                                Text(
                                    text = "Mascot Critique Response",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = mascotCritique,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // COGNITIVE SLIDERS CARD
        Card(
            modifier = Modifier.fillMaxWidth().testTag("mascot_tuning_panel"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Mascot Scribe Tuning Panel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Adjust the parameters of our translation lore generator. Modulates humor, local proverb frequency, and low-latency cache depth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Proverb Wit Frequency", style = MaterialTheme.typography.labelSmall)
                        Text("${(translationSpeed * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = translationSpeed,
                        onValueChange = { translationSpeed = it },
                        modifier = Modifier.testTag("translation_speed_slider")
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Language Sarcasm Coeff", style = MaterialTheme.typography.labelSmall)
                        Text("${(emotionalScale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = emotionalScale,
                        onValueChange = { emotionalScale = it },
                        modifier = Modifier.testTag("emotional_scale_slider")
                    )
                }
            }
        }
    }
}

@Composable
fun UniversalMobileVideoCallIntegrationSuite(viewModel: TranslationViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val sourceLang by viewModel.sourceLanguage.collectAsState()
    val targetLang by viewModel.targetLanguage.collectAsState()
    
    var selectedIntegTab by remember { mutableStateOf("voip") } // "voip", "gsm", "facetime", "android_video"
    var isSimulatingInteg by remember { mutableStateOf(false) }
    
    var lastSimulatedSpeechText by remember { mutableStateOf("") }
    var lastSimulatedTranslationText by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("universal_integration_suite_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // HEADER
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Smartphone,
                    contentDescription = "Universal Integration Hub",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Mobile & Video Call Integration Hub",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Explore the engineering feasibility & architectural specifications of running AnuVedhai on system dialers, VOIP apps, and FaceTime video platforms.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

            // TAB SELECTOR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    "voip" to "VoIP Apps",
                    "gsm" to "Cellular GSM",
                    "facetime" to "FaceTime",
                    "android_video" to "Andr Video"
                )
                tabs.forEach { (tabId, label) ->
                    val isSelected = selectedIntegTab == tabId
                    Button(
                        onClick = { 
                            selectedIntegTab = tabId
                            isSimulatingInteg = false
                            lastSimulatedSpeechText = ""
                            lastSimulatedTranslationText = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("integ_tab_$tabId"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // DYNAMIC FEASIBILITY CONTAINER
            when (selectedIntegTab) {
                "voip" -> {
                    IntegDetailsSection(
                        feasibility = "🟢 HIGHLY FEASIBLE (Dynamic Loopbacks)",
                        feasibilityColor = Color(0xFF25D366),
                        summary = "VOIP pipelines like WhatsApp, Viber, or Telegram transmit raw audio data streams over IP. On modern devices, we intercept active VoIP call audio frames either using system-wide Accessibility Service listeners, or by initiating Android's local AudioPlaybackCapture API combined with background media capture.",
                        techDescription = "This approach records high-fidelity digital streams from targeted communication application nodes directly. The intercepted raw PCM audio buffers are chunked, downsampled to 16kHz mono, and streamed to the low-latency translation engines.",
                        codeLabel = "Android Kotlin VoIP Capture Configuration:",
                        codeSnippet = """
val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
    .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
    .build()

val record = AudioRecord.Builder()
    .setAudioPlaybackCaptureConfig(config)
    .setAudioFormat(AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(16000)
        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
    .build()
                        """.trimIndent(),
                        clipboardManager = clipboardManager,
                        context = context
                    )
                }
                "gsm" -> {
                    IntegDetailsSection(
                        feasibility = "🟡 CONDITIONAL (OS Sandboxed Workaround)",
                        feasibilityColor = Color(0xFFFF9800),
                        summary = "Standard carrier cellular calls are highly protected by iOS and Android's OS-level security sandboxing to prevent spyware and eavesdropping. Application code cannot access 'Voice Call' media sources directly.",
                        techDescription = "To resolve this in production, create a cloud-based SIP Telephony Bridge (e.g. Twilio/Asterisk gateway). The mobile app initiates calls through our web gateway. The gateway bridges the dialer over PSTN, duplicates active RTP speech packets serverside, runs ultra-low-latency Gemini transcription, and emits real-time bilingual subtitle streams back to the mobile screen via WebSockets.",
                        codeLabel = "Production SIP Telephony Client Stream Connection:",
                        codeSnippet = """
// Route cellular call audio serverside using WebSockets & Twilio SIP bridge
val callParams = mapOf(
    "to" to "+919876543210",
    "translateTo" to "HINDI",
    "transcribe" to "true",
    "voiceEngine" to "AnuVedhai-Sarvam-v1"
)
val twilioCall = TwilioVoice.connect(accessToken, ConnectOptions.Builder()
    .params(callParams)
    .build())
                        """.trimIndent(),
                        clipboardManager = clipboardManager,
                        context = context
                    )
                }
                "facetime" -> {
                    IntegDetailsSection(
                        feasibility = "🟢 EXCELLENT (Apple SharePlay SDK)",
                        feasibilityColor = Color(0xFF007AFF),
                        summary = "Apple provides fully supported, native system APIs to embed synchronous applications in FaceTime video calls via SharePlay, keeping multiple callers perfectly in sync.",
                        techDescription = "Use Swift's GroupActivities framework. During FaceTime, users launch the AnuVedhai session which synchronizes translation queues at sub-100ms lag. To capture partner audio, configure a system Broadcast Upload extension (ReplayKit), and draw live translated floating overlays atop other apps using AVPictureInPictureController.",
                        codeLabel = "iOS Swift SharePlay Sync Activity Protocol:",
                        codeSnippet = """
import GroupActivities

struct FaceTimeTranslationActivity: GroupActivity {
    static let activityIdentifier = "com.anuvedhai.facetime.subtitles"
    
    var metadata: GroupActivityMetadata {
        var meta = GroupActivityMetadata()
        meta.title = "AnuVedhai Live Subtitles"
        meta.type = .generic
        meta.sceneAssociationBehavior = .none
        return meta
    }
}
                        """.trimIndent(),
                        clipboardManager = clipboardManager,
                        context = context
                    )
                }
                "android_video" -> {
                    IntegDetailsSection(
                        feasibility = "🟢 NATIVE COMPLETE (MediaProjection Overlay)",
                        feasibilityColor = Color(0xFF00E5FF),
                        summary = "Custom, seamless translation over Zoom, Google Meet, or native carrier video dialers on Android works natively using drawing overlays & the MediaProjection screen capture SDK.",
                        techDescription = "Register for SYSTEM_ALERT_WINDOW permission. This allows your app to inject and render beautiful transparent, draggable composable subtitles on a floating window layout atop third-party applications. Screen content and audio are intercepted on-device and translated concurrently.",
                        codeLabel = "Android Kotlin Window Overlay Injection System:",
                        codeSnippet = """
val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    y = 150 // overlay bottom margin
}
windowManager.addView(floatingComposeSubtitleView, params)
                        """.trimIndent(),
                        clipboardManager = clipboardManager,
                        context = context
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // INTERACTIVE FEASIBILITY EMULATOR/SIMULATOR
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("interactive_feasibility_simulator")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛠️ Functional Stream Simulator",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSimulatingInteg) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Red.copy(alpha = dotAlpha), CircleShape)
                                )
                                Text(
                                    text = "SIM ACTIVE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                            }
                        }
                    }

                    Text(
                        text = "Simulate active interface channels to test translation overlays. Tapping the simulation buttons routes synthetic digital payloads through our parsing queue to represent live operations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isSimulatingInteg) {
                        Button(
                            onClick = {
                                isSimulatingInteg = true
                                lastSimulatedSpeechText = "Listening to media frames..."
                                lastSimulatedTranslationText = "Establishing decryption..."
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("start_integ_sim_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("Initialize Loopback Capture Channel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // ACTIVE SIM DISPLAY MODULE
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Captured Loopback Feed:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            val statusMsg = when (selectedIntegTab) {
                                "voip" -> "🟢 Capturing dynamic buffer array from: WhatsApp (VoIP Mode 48K MONO)"
                                "gsm" -> "🔵 Capturing serverside voice stream trunk: Twilio PSTN Gateway Bridge"
                                "facetime" -> "🟢 Capturing FaceTime buffers: iOS Apple SharePlay Session Stream"
                                "android_video" -> "🟢 Capturing system media screen frames: Android MediaProjection"
                                else -> ""
                            }
                            Text(
                                text = statusMsg,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                            val phrasesRepo = when (selectedIntegTab) {
                                "voip" -> listOf(
                                    "Is the audio clear? I am speaking on the train!" to "क्या आवाज साफ है? मैं ट्रेन में बोल रहा हूँ!",
                                    "Meeting started already, join immediately!" to "बैठक पहले ही शुरू हो चुकी है, तुरंत शामिल हों!"
                                )
                                "gsm" -> listOf(
                                    "Hello, this is standard calling cell, and audio is clear." to "नमस्ते, यह मानक कॉलिंग सेल है, और आवाज स्पष्ट है।",
                                    "Please sign the delivery package today." to "कृपया आज ही डिलीवरी पैकेज पर हस्ताक्षर करें।"
                                )
                                "facetime" -> listOf(
                                    "Hi inside FaceTime video, are the overlays updating nicely?" to "हाय फेसटाइम वीडियो के अंदर, क्या ओवरले अच्छी तरह से अपडेट हो रहे हैं?",
                                    "Look at this gorgeous layout in Apple landscape!" to "एप्पल लैंडस्केप में इस सुंदर लेआउट को देखें!"
                                )
                                "android_video" -> listOf(
                                    "Testing Android video call subtitle injection." to "एंड्रॉइड वीडियो कॉल उपशीर्षक इंजेक्शन का परीक्षण।",
                                    "Draw transparent overlays perfectly under system status bar." to "सिस्टम स्टेटस बार के तहत पारदर्शी ओवरले पूरी तरह से ड्रा करें।"
                                )
                                else -> emptyList()
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val (partnerMsg, ourTranslation) = phrasesRepo.random()
                                        lastSimulatedSpeechText = partnerMsg
                                        lastSimulatedTranslationText = ourTranslation
                                        viewModel.simulateCallTranslation(
                                            textInput = partnerMsg,
                                            partnerLang = LanguageOptions[0], // English as active partner trigger
                                            ourLang = LanguageOptions[1], // Hindi
                                            isPartnerSpeaking = true
                                        )
                                        Toast.makeText(context, "Injected simulation frame", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp).testTag("sim_partner_speech_btn"),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Simulate Partner Speech", fontSize = 9.sp)
                                }

                                Button(
                                    onClick = {
                                        val responses = listOf(
                                            "Yes, I see the live dynamic overlays instantly!" to "हाँ, मैं लाइव गतिशील ओवरले तुरंत देखता हूँ!",
                                            "This translation pipeline is highly responsive." to "यह अनुवाद पाइपलाइन अत्यधिक प्रतिक्रियाशील है।"
                                        )
                                        val (ourMsg, partnerTrans) = responses.random()
                                        lastSimulatedSpeechText = ourMsg
                                        lastSimulatedTranslationText = partnerTrans
                                        viewModel.simulateCallTranslation(
                                            textInput = ourMsg,
                                            partnerLang = LanguageOptions[0],
                                            ourLang = LanguageOptions[1],
                                            isPartnerSpeaking = false
                                        )
                                        Toast.makeText(context, "Injected simulation response", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp).testTag("sim_our_speech_btn"),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Simulate Our Response", fontSize = 9.sp)
                                }
                            }

                            if (lastSimulatedSpeechText.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Text("Captured Audio:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(lastSimulatedSpeechText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Decoded Translation:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(lastSimulatedTranslationText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Button(
                                onClick = {
                                    isSimulatingInteg = false
                                    lastSimulatedSpeechText = ""
                                    lastSimulatedTranslationText = ""
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .height(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Close Channel Feed", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntegDetailsSection(
    feasibility: String,
    feasibilityColor: Color,
    summary: String,
    techDescription: String,
    codeLabel: String,
    codeSnippet: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = feasibilityColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = feasibility,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = feasibilityColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.sp
        )

        Text(
            text = techDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = codeLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(codeSnippet))
                    android.widget.Toast.makeText(context, "System boilerplate copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp).testTag("copy_integ_code_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text = codeSnippet,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
