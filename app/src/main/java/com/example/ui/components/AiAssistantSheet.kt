package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AiMessage
import com.example.data.model.CartAction
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
    role: String,
    messages: List<AiMessage>,
    isLoading: Boolean,
    isLiveGeminiEnabled: Boolean,
    onSendMessage: (String) -> Unit,
    onAddToCartFromAi: (messageId: String, actions: List<CartAction>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var isSpeechEnabled by remember { mutableStateOf(true) }
    var speakingMsgId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val fil = Locale("fil", "PH")
                val res = engine?.setLanguage(fil)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine?.setLanguage(Locale.US)
                }
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onSendMessage(spoken)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val (botName, botTitle, suggestions) = remember(role) {
        when (role) {
            "admin" -> Triple(
                "Ka-Sosyong AI",
                "AI Business Advisor & Co-pilot",
                listOf(
                    "Suriin ang mga low-stock items ngayon.",
                    "Paano mapapabuti ang kita ng branch?",
                    "Tips sa fast-moving inventory."
                )
            )
            "rider" -> Triple(
                "Kuya Dispatch",
                "Rider Dispatch Assistant",
                listOf(
                    "Paano i-optimize ang ruta sa Bypass Road?",
                    "Tips para sa ligtas na pagbiyahe sa ulan.",
                    "Paano i-verify ang POD ng customer?"
                )
            )
            else -> Triple(
                "Ate Digy",
                "Silang Mini Mart Shopping Helper",
                listOf(
                    "Ano ang mga sikat na produkto rito?",
                    "Paano makatipid sa Tingi vs Pakyaw?",
                    "Pabili ng 2 kilos na bigas"
                )
            )
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            tts?.stop()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = SilangSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 620.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SilangSlate900)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SilangOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = SilangWhite)
                    }
                    Column {
                        Text(botName, style = MaterialTheme.typography.titleMedium, color = SilangOrangeBright)
                        Text(botTitle, style = MaterialTheme.typography.bodySmall, color = SilangSlate400)
                    }
                }
                Row {
                    IconButton(
                        onClick = {
                            if (isSpeechEnabled) tts?.stop()
                            isSpeechEnabled = !isSpeechEnabled
                        }
                    ) {
                        Icon(
                            if (isSpeechEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = stringResource(R.string.cd_speak_response),
                            tint = if (isSpeechEnabled) SilangOrangeBright else SilangSlate400
                        )
                    }
                    IconButton(onClick = {
                        tts?.stop()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SilangWhite)
                    }
                }
            }

            if (!isLiveGeminiEnabled) {
                Surface(color = SilangAmberContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.ai_preview_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = SilangSlate900,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                        bottomEnd = if (msg.isUser) 4.dp else 16.dp
                                    )
                                )
                                .background(if (msg.isUser) SilangSlate900 else SilangWhite)
                                .border(
                                    1.dp,
                                    if (msg.isUser) SilangSlate900 else SilangSlate200,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (msg.isUser) "IKAW" else botName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (msg.isUser) SilangOrangeBright else SilangSlate400
                            )
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (msg.isUser) SilangWhite else SilangSlate900
                            )

                            if (!msg.isUser) {
                                TextButton(
                                    onClick = {
                                        if (speakingMsgId == msg.id) {
                                            tts?.stop()
                                            speakingMsgId = null
                                        } else {
                                            speakingMsgId = msg.id
                                            tts?.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, msg.id)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.heightIn(min = 36.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = SilangOrange)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pakinggan (Tagalog)", fontSize = 11.sp, color = SilangOrange)
                                }
                            }

                            if (!msg.isUser && msg.cartActions.isNotEmpty()) {
                                msg.cartActions.forEach { action ->
                                    Button(
                                        onClick = { onAddToCartFromAi(msg.id, msg.cartActions) },
                                        enabled = !msg.cartActionDone,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (msg.cartActionDone) SilangGreen else SilangOrange
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (msg.cartActionDone) "Added to Bag"
                                            else "Add ${action.quantity} ${action.unitName} ${action.productName}",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (isLoading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SilangOrange)
                            Text("Nag-iisip si $botName...", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                        }
                    }
                }
            }

            // Suggestion chips
            if (messages.size <= 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { onSendMessage(suggestion) },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SilangWhite)
                    .border(1.dp, SilangSlate200)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fil-PH")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Magsalita sa Tagalog o English...")
                        }
                        runCatching { speechLauncher.launch(intent) }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SilangOrangeContainer)
                        .testTag("ai_voice_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.cd_voice_input), tint = SilangOrange)
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Magtanong kay $botName...", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f).testTag("ai_chat_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val msg = inputText
                            inputText = ""
                            onSendMessage(msg)
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank() && !isLoading) SilangOrange else SilangSlate200)
                        .testTag("ai_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = SilangWhite)
                }
            }
        }
    }
}
