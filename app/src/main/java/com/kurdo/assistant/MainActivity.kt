package com.kurdo.assistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val MODEL_FILE = "kurdo-qwen-0.8b.litertlm"
private const val MODEL_URL = "https://huggingface.co/litert-community/Qwen3.5-0.8B/resolve/main/Qwen3.5-0.8B_int8.litertlm?download=true"
private const val MODEL_BYTES = 964657152L
private val MODEL_LOCK = Any()

data class ChatLine(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { KurdoScreen(onSpeak = { speak(it) }, onListen = { listen(it) }) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("tr", "TR")
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kurdo_reply")
    }

    private fun listen(callback: (String) -> Unit) {
        val available = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        if (!available) {
            callback("Bu telefonda çevrimdışı konuşma tanıma hizmeti bulunamadı. Ses girişi bu nedenle kullanılamıyor.")
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
            override fun onError(error: Int) { callback("Sesi anlayamadım, tekrar deneyebilir misin?") }
            override fun onResults(results: Bundle?) {
                callback(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        speechRecognizer?.startListening(request)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

private class LocalKurdoModel(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun load() = withContext(Dispatchers.IO) {
        synchronized(MODEL_LOCK) {
            if (conversation != null) return@withContext
        }
        val model = File(context.filesDir, MODEL_FILE)
        check(model.exists() && model.length() > 100_000_000L) { "Yerel model dosyası eksik veya tamamlanmamış." }
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        val newEngine = Engine(EngineConfig(
            modelPath = model.absolutePath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath
        ))
        try {
            newEngine.initialize()
            val newConversation = newEngine.createConversation(ConversationConfig(
                systemInstruction = Contents.of(
                    "Sen Kurdo'sun; Seçkin'in telefonda çalışan kişisel yardımcısısın. " +
                    "Türkçe konuş, sıcak ve doğal ol; gerektiğinde Iğdır yöresinden hafif bir ağız kullan ama anlaşılır kal. " +
                    "Kısa ve işe yarar cevap ver. Bilmediğin şeyi uydurma. Telefon işlemlerinde kullanıcı onayını bekle."
                )
            ))
            synchronized(MODEL_LOCK) {
                conversation?.close()
                engine?.close()
                engine = newEngine
                conversation = newConversation
            }
        } catch (error: Throwable) {
            newEngine.close()
            throw error
        }
    }

    suspend fun answer(question: String, memories: List<String>): String = withContext(Dispatchers.IO) {
        val active = synchronized(MODEL_LOCK) { conversation }
            ?: error("Önce yerel modeli hazırla.")
        val note = if (memories.isEmpty()) "" else "
Kullanıcının cihazda saklanan notları:
" + memories.joinToString("
")
        val output = StringBuilder()
        active.sendMessageAsync(note + "
Kullanıcı: " + question).collect { output.append(it.toString()) }
        output.toString().trim().ifBlank { "Cevap üretemedim; bir kez daha dener misin?" }
    }

    fun close() {
        synchronized(MODEL_LOCK) {
            conversation?.close()
            engine?.close()
            conversation = null
            engine = null
        }
    }
}

private suspend fun downloadModel(context: Context, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
    val destination = File(context.filesDir, MODEL_FILE)
    if (destination.exists() && destination.length() == MODEL_BYTES) return@withContext destination
    val partial = File(context.filesDir, MODEL_FILE + ".part")
    val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20000
        readTimeout = 60000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Kurdo-Android")
    }
    try {
        connection.connect()
        check(connection.responseCode in 200..299) { "Model sunucusu HTTP " + connection.responseCode + " yanıtı verdi." }
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: MODEL_BYTES
        connection.inputStream.use { input ->
            FileOutputStream(partial).use { output ->
                val buffer = ByteArray(1024 * 1024)
                var copied = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                    Handler(Looper.getMainLooper()).post { progress(copied, total) }
                }
                output.fd.sync()
                check(copied == MODEL_BYTES) { "Model boyutu beklenenden farklı. İndirme tamamlanmadı; tekrar deneyebilirsin." }
            }
        }
        check(partial.renameTo(destination)) { "Model dosyası telefona kaydedilemedi." }
        destination
    } catch (error: Throwable) {
        partial.delete()
        throw error
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun KurdoScreen(onSpeak: (String) -> Unit, onListen: ((String) -> Unit) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kurdo_local", Context.MODE_PRIVATE) }
    val runtime = remember { LocalKurdoModel(context.applicationContext) }
    val messages = remember {
        mutableStateListOf<ChatLine>().apply {
            val saved = runCatching { JSONArray(prefs.getString("chat", "[]")) }.getOrNull()
            if (saved != null) for (i in 0 until saved.length()) {
                val item = saved.optJSONObject(i) ?: continue
                add(ChatLine(item.optString("text"), item.optBoolean("user")))
            }
            if (isEmpty()) add(ChatLine("Hele gardaş, ben Kurdo. Modeli indirince bu telefonda çevrimdışı çalışacağım.", false))
        }
    }
    val memories = remember {
        mutableStateListOf<String>().apply {
            val saved = runCatching { JSONArray(prefs.getString("memories", "[]")) }.getOrNull()
            if (saved != null) for (i in 0 until saved.length()) add(saved.optString(i))
        }
    }
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var modelReady by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var progressLabel by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("İlk kullanımda yerel modeli Wi-Fi ile indir. Sonrasında yanıtlar telefonda üretilir.") }
    var errorText by remember { mutableStateOf("") }
    var pendingTorch by remember { mutableStateOf<Boolean?>(null) }

    fun saveChat() {
        val json = JSONArray()
        messages.takeLast(50).forEach { json.put(JSONObject().put("text", it.text).put("user", it.fromUser)) }
        prefs.edit().putString("chat", json.toString()).apply()
    }
    fun saveMemories() {
        val json = JSONArray()
        memories.takeLast(60).forEach { json.put(it) }
        prefs.edit().putString("memories", json.toString()).apply()
    }
    fun captureMemory(text: String) {
        val clean = text.trim().take(180)
        val lower = clean.lowercase(Locale("tr", "TR"))
        val name = Regex("""(?:benim adım|adım)\s+([\p{L}]+(?:\s+[\p{L}]+)?)""", RegexOption.IGNORE_CASE).find(clean)
        val memory = when {
            name != null -> "Kullanıcının adı: " + name.groupValues[1]
            listOf("seviyorum", "hoşlanıyorum", "tercih ederim", "tercihim", "istemiyorum", "kullanıyorum", "motorum", "aracım").any { lower.contains(it) } -> clean
            else -> null
        }
        if (memory != null && memory !in memories) {
            memories.add(memory)
            while (memories.size > 60) memories.removeAt(0)
            saveMemories()
        }
    }
    fun setTorch(enabled: Boolean) {
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camera = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            if (camera == null) {
                messages.add(ChatLine("Bu telefonda fener kullanılamıyor.", false))
            } else {
                manager.setTorchMode(camera, enabled)
                messages.add(ChatLine(if (enabled) "Feneri açtım." else "Feneri kapattım.", false))
            }
        } catch (error: Exception) {
            messages.add(ChatLine("Fener açılamadı: " + (error.message ?: "izin veya donanım sorunu"), false))
        }
        saveChat()
    }
    val torchPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = pendingTorch
        pendingTorch = null
        if (granted && requested != null) setTorch(requested)
        else if (!granted) messages.add(ChatLine("Fener için kamera izni gerekiyor.", false))
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onListen { recognized -> if (recognized.isNotBlank()) draft = recognized }
        else status = "Sesle yazmak için mikrofon iznini açman gerekiyor."
    }

    LaunchedEffect(Unit) {
        val model = File(context.filesDir, MODEL_FILE)
        if (model.exists() && model.length() == MODEL_BYTES) {
            status = "İndirilen yerel model hazırlanıyor…"
            try {
                runtime.load()
                modelReady = true
                status = "Kurdo hazır; sohbet cihazda çevrimdışı çalışıyor."
            } catch (error: Throwable) {
                errorText = error.message ?: "Model açılamadı."
                status = "Yerel model açılamadı."
            }
        }
    }
    LaunchedEffect(messages.size) { saveChat() }

    fun localAction(text: String): String? {
        val lower = text.lowercase(Locale("tr", "TR"))
        if (lower.contains("fener") || lower.contains("ışık")) {
            val enabled = lower.contains("aç") || lower.contains("yak")
            val disabled = lower.contains("kapat") || lower.contains("söndür")
            if (enabled || disabled) {
                pendingTorch = enabled
                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) setTorch(enabled)
                else torchPermission.launch(Manifest.permission.CAMERA)
                return ""
            }
        }
        val call = Regex("""(?:ara|telefon et)\s+([+0-9][0-9 +()\-]{5,})""", RegexOption.IGNORE_CASE).find(text)
        if (call != null) {
            val number = call.groupValues[1].replace(Regex("[^+0-9]"), "")
            return try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)))
                "Arama ekranını açtım; aramayı sen onayla."
            } catch (_: ActivityNotFoundException) { "Bu telefonda arama uygulaması bulunamadı." }
        }
        val sms = Regex("""mesaj\s+(?:gönder|yaz)\s+([+0-9][0-9 +()\-]{5,})\s+(.+)""", RegexOption.IGNORE_CASE).find(text)
        if (sms != null) {
            val number = sms.groupValues[1].replace(Regex("[^+0-9]"), "")
            return try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number)).putExtra("sms_body", sms.groupValues[2])
                context.startActivity(intent)
                "Mesaj uygulamasını açtım; göndermeden önce metni kontrol edip sen onayla."
            } catch (_: ActivityNotFoundException) { "Bu telefonda mesaj uygulaması bulunamadı." }
        }
        return null
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || working) return
        draft = ""
        messages.add(ChatLine(text, true))
        captureMemory(text)
        val action = localAction(text)
        if (action != null) {
            if (action.isNotBlank()) messages.add(ChatLine(action, false))
            return
        }
        if (!modelReady) {
            messages.add(ChatLine("Yanıt verebilmem için önce aşağıdaki yerel modeli indirip hazırlaman gerekiyor.", false))
            return
        }
        working = true
        scope.launch {
            try {
                val response = runtime.answer(text, memories.toList())
                messages.add(ChatLine(response, false))
                onSpeak(response)
            } catch (error: Throwable) {
                messages.add(ChatLine("Yanıt üretirken sorun çıktı: " + (error.message ?: "bilinmeyen hata"), false))
            } finally {
                working = false
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF63D7CF),
        background = Color(0xFF101719),
        surface = Color(0xFF192225)
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101719)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("KURDO", color = Color(0xFF63D7CF), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Kişisel yardımcın · cihazında özel", color = Color.LightGray, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF192225))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(status, color = Color.White, fontSize = 14.sp)
                        if (!modelReady) {
                            Spacer(Modifier.height(8.dp))
                            Button(enabled = !working, onClick = {
                                working = true
                                errorText = ""
                                status = "Yaklaşık 965 MB yerel model indiriliyor…"
                                scope.launch {
                                    try {
                                        val model = downloadModel(context) { copied, total ->
                                            progress = copied.toFloat() / total.toFloat()
                                            progressLabel = "%.0f%% · %.0f / %.0f MB".format(Locale.US, copied / 1048576f, total / 1048576f)
                                        }
                                        status = "Model indirildi; telefonda hazırlanıyor…"
                                        runtime.load()
                                        modelReady = true
                                        status = "Kurdo hazır; sohbet cihazda çevrimdışı çalışıyor."
                                        errorText = ""
                                    } catch (error: Throwable) {
                                        errorText = error.message ?: "İndirme veya model hazırlama başarısız oldu."
                                        status = "Kurdo hazırlanamadı."
                                    } finally { working = false }
                                }
                            }) { Text(if (File(context.filesDir, MODEL_FILE).exists()) "Yerel modeli hazırla" else "Yerel modeli indir · yaklaşık 965 MB") }
                            if (progress > 0f && progress < 1f) {
                                Spacer(Modifier.height(6.dp))
                                androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Text(progressLabel, color = Color.LightGray, fontSize = 12.sp)
                            }
                            if (errorText.isNotBlank()) Text(errorText, color = Color(0xFFFF9B8F), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Yerel hafıza: " + memories.size + " not · bu telefonda saklanır", color = Color(0xFFB5C7C8), fontSize = 12.sp)
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages) { line ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.88f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = if (line.fromUser) Color(0xFF164B4A) else Color(0xFF202B2E))
                            ) { Text(line.text, modifier = Modifier.padding(12.dp), color = Color.White, fontSize = 15.sp) }
                        }
                    }
                    if (working && modelReady) item { CircularProgressIndicator(Modifier.size(24.dp), color = Color(0xFF63D7CF)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Kurdo'ya yaz…") },
                        maxLines = 4
                    )
                    Button(onClick = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            onListen { recognized -> if (recognized.isNotBlank()) draft = recognized }
                        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { androidx.compose.material3.Icon(Icons.Default.Mic, contentDescription = "Sesle yaz") }
                    Button(enabled = !working, onClick = { send() }) {
                        androidx.compose.material3.Icon(Icons.Default.Send, contentDescription = "Gönder")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Telefon komutları: ‘feneri aç’, ‘ara 05…’, ‘mesaj gönder 05… metin’", color = Color(0xFF90A4A6), fontSize = 11.sp)
            }
        }
    }
}
