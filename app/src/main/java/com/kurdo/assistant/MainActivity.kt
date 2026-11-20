package com.kurdo.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ChatLine(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechResult: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { KurdoScreen(
            onSpeak = { speak(it) },
            onListen = { callback -> listen(callback) }
        ) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("tr", "TR")
            // Ses motorunun Türkçe erkek sesi sunması cihazdaki yüklü seslere bağlıdır.
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kurdo_reply")
    }

    private fun listen(callback: (String) -> Unit) {
        val onDevice = android.os.Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        if (!onDevice) {
            callback("Bu telefonda yerel konuşma tanıma hizmeti bulunamadı. Kurdo internet kullanmamak için çevrimiçi tanımaya geçmedi.")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { callback("Ses algılanamadı. Tekrar deneyebilirsin.") }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                callback(spoken)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun KurdoScreen(onSpeak: (String) -> Unit, onListen: ((String) -> Unit) -> Unit) {
    val prefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("kurdo_local", android.content.Context.MODE_PRIVATE)
    val messages = remember {
        mutableStateListOf<ChatLine>().apply {
            add(ChatLine("Hele gardaş, ben Kurdo. Daha yeni tanışırık; konuştukça seni tanımayı öğrenecem.", false))
        }
    }
    val memories = remember {
        mutableStateListOf<String>().apply {
            val raw = prefs.getString("memories", "[]") ?: "[]"
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) add(arr.getString(i))
            } catch (_: Exception) {}
        }
    }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Cihaz içi hafıza hazır") }
    var useDialect by remember { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val micPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) status = "Mikrofon izni verilmedi"
    }

    fun saveMemory(text: String) {
        val clean = text.trim()
        val triggers = listOf("benim", "seviyorum", "istemiyorum", "projem", "telefonum", "tercihim", "hatırla", "adım")
        if (triggers.any { clean.lowercase().contains(it) } && clean.length in 8..240) {
            val item = clean.take(240)
            if (!memories.contains(item)) {
                memories.add(0, item)
                while (memories.size > 100) memories.removeAt(memories.lastIndex)
                val arr = JSONArray()
                memories.forEach { arr.put(it) }
                prefs.edit().putString("memories", arr.toString()).apply()
            }
        }
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        messages.add(ChatLine(text, true))
        saveMemory(text)
        val reply = when {
            text.contains("hafıza", true) || text.contains("ne hatırl", true) ->
                if (memories.isEmpty()) "Hele gardaş, daha hafızama kaydettiğim bir bilgi yok."
                else "Şimdilik bunları hafızama almışam: " + memories.take(5).joinToString("; ")
            text.contains("merhaba", true) || text.contains("selam", true) ->
                "Hee gardaş, hoş geldin. Ne konuşak bugün?"
            else -> "Hele gardaş, mesajını aldım. Şu an bu ilk prototipte yerel sohbet ve hafıza çalışıyor. Asıl yapay zekâ modelini telefona eklediğimizde sorularını çevrimdışı cevaplayabileceğim."
        }
        messages.add(ChatLine(reply, false))
        onSpeak(reply)
        input = ""
    }

    val bg = Color(0xFF0B1220)
    val panel = Color(0xFF172337)
    val cyan = Color(0xFF38BDF8)

    MaterialTheme(colorScheme = darkColorScheme(
        primary = cyan, background = bg, surface = panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Color(0xFF123B55), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Memory, null, tint = cyan)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("KURDO", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(status, fontSize = 11.sp, color = cyan)
                }
                Text("8 GB • Yerel", color = Color.LightGray, fontSize = 11.sp)
            }
            Divider(color = Color(0xFF263449))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Iğdır ağzı", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = useDialect, onCheckedChange = { useDialect = it })
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement =
                        if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (msg.fromUser) Color(0xFF075985) else panel,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Text(msg.text, Modifier.padding(14.dp), color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
            if (memories.isNotEmpty()) {
                Text("Hafızada ${memories.size} bilgi", color = cyan,
                    modifier = Modifier.padding(start = 18.dp, bottom = 6.dp), fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth().padding(12.dp).background(panel, RoundedCornerShape(28.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        onListen { heard -> if (heard.isNotBlank()) send(heard) }
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) { Icon(Icons.Default.Mic, "Konuş", tint = cyan) }
                BasicTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                    decorationBox = { inner ->
                        if (input.isEmpty()) Text("Kurdo'ya bir şey anlat...", color = Color.Gray)
                        inner()
                    }
                )
                IconButton(onClick = { send(input) }) {
                    Icon(Icons.Default.Send, "Gönder", tint = cyan)
                }
            }
            Text("İlk prototip • Model henüz eklenmedi • Veriler bu cihazda saklanır",
                color = Color.Gray, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
        }
    }
}
