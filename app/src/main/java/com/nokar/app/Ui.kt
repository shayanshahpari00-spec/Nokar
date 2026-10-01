package com.nokar.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

private val Purple = Color(0xFF5B4BDB)
private val Yellow = Color(0xFFFFC53D)
private val Bg = Color(0xFFF6F4FF)

@Composable
fun NokarApp(speak: (String) -> Unit, stopSpeak: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Purple, secondary = Yellow)) {
            ChatScreen(speak, stopSpeak)
        }
    }
}

@Composable
fun NokarAvatar(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(Purple, radius = s / 2)
        drawCircle(Yellow, radius = s * 0.40f, center = Offset(s / 2, s * 0.54f))
        val hat = Path().apply {
            moveTo(s * 0.5f, s * 0.08f); lineTo(s * 0.88f, s * 0.24f)
            lineTo(s * 0.5f, s * 0.40f); lineTo(s * 0.12f, s * 0.24f); close()
        }
        drawPath(hat, Color(0xFF2B2370))
        drawRect(Color(0xFF2B2370), Offset(s * 0.30f, s * 0.30f), Size(s * 0.40f, s * 0.10f))
        drawCircle(Color.White, s * 0.075f, Offset(s * 0.38f, s * 0.52f))
        drawCircle(Color.White, s * 0.075f, Offset(s * 0.62f, s * 0.52f))
        drawCircle(Color.Black, s * 0.035f, Offset(s * 0.39f, s * 0.53f))
        drawCircle(Color.Black, s * 0.035f, Offset(s * 0.63f, s * 0.53f))
        val smile = Path().apply {
            moveTo(s * 0.35f, s * 0.67f)
            quadraticTo(s * 0.5f, s * 0.82f, s * 0.65f, s * 0.67f)
        }
        drawPath(smile, Color.Black, style = Stroke(s * 0.03f, cap = StrokeCap.Round))
    }
}

@Composable
fun ChatScreen(speak: (String) -> Unit, stopSpeak: () -> Unit) {
    val ctx = LocalContext.current
    val vm: ChatVM = viewModel()
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reply: (String) -> Unit = { if (vm.prefs.autoSpeak) speak(it) }
    fun doSend(t: String) { stopSpeak(); vm.send(t, reply); input = "" }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx) else null
    }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }
    fun startListening() {
        if (recognizer == null) {
            Toast.makeText(ctx, "تشخیص گفتار روی این گوشی در دسترس نیست.", Toast.LENGTH_LONG).show(); return
        }
        stopSpeak()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                listening = false
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { doSend(it) }
            }
            override fun onError(e: Int) {
                listening = false
                Toast.makeText(ctx, "صدایت را نشنیدم، دوباره امتحان کن 🙂", Toast.LENGTH_SHORT).show()
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
        }
        listening = true
        recognizer.startListening(i)
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startListening() }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::setImage) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) camUri?.let(vm::setImage)
    }
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        val f = File.createTempFile("p", ".jpg", dir)
        val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        camUri = u; camera.launch(u)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) launchCamera() }

    LaunchedEffect(vm.msgs.size, vm.loading) {
        val n = vm.msgs.size + if (vm.loading) 1 else 0
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(Purple).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NokarAvatar(44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("نوکر", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("رفیق درسی پایه نهم", color = Color(0xFFD9D4FF), fontSize = 12.sp)
            }
            TextButton(onClick = { stopSpeak(); vm.newChat() }) { Text("گفتگوی نو", color = Color.White) }
            TextButton(onClick = { showSettings = true }) { Text("⚙️", fontSize = 20.sp) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SUBJECTS) { s ->
                FilterChip(selected = vm.subject == s, onClick = { vm.subject = s }, label = { Text(s) })
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (vm.msgs.isEmpty()) item { Welcome(onPick = { doSend(it) }) }
            items(vm.msgs) { Bubble(it) }
            if (vm.loading) item { Bubble(Msg("assistant", "نوکر داره فکر می‌کنه… 🤔")) }
            vm.error?.let { e -> item { Text(e, color = Color(0xFFB3261E), modifier = Modifier.padding(4.dp)) } }
        }
        vm.pendingImage?.let { b ->
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(b.asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                TextButton(onClick = { vm.pendingImage = null }) { Text("حذف عکس") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn("📷") {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == 0) launchCamera()
                else camPerm.launch(Manifest.permission.CAMERA)
            }
            IconBtn("🖼️") { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f), placeholder = { Text("سؤالت رو بنویس…") },
                shape = RoundedCornerShape(24.dp), maxLines = 4
            )
            Spacer(Modifier.width(6.dp))
            if (input.isBlank() && vm.pendingImage == null) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(if (listening) Color(0xFFE53935) else Yellow)
                        .clickable {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == 0) startListening()
                            else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                        }, contentAlignment = Alignment.Center
                ) { Text(if (listening) "👂" else "🎤", fontSize = 24.sp) }
            } else {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Purple).clickable { doSend(input) },
                    contentAlignment = Alignment.Center
                ) { Text("➤", color = Color.White, fontSize = 22.sp) }
            }
        }
    }
    if (showSettings) SettingsDialog(vm.prefs) { showSettings = false }
}

@Composable
private fun IconBtn(t: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, fontSize = 22.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Welcome(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NokarAvatar(110.dp)
        Spacer(Modifier.height(12.dp))
        Text("سلام رفیق! من نوکرم 😎", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Purple)
        Text("هر درسی که گیر کردی بپرس؛ تایپ کن، حرف بزن یا عکس بفرست.", modifier = Modifier.padding(8.dp))
        listOf("یک درس ریاضی رو برام ساده توضیح بده", "از من یه سؤال تمرینی علوم بپرس").forEach {
            AssistChip(onClick = { onPick(it) }, label = { Text(it) }, modifier = Modifier.padding(2.dp))
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                .background(if (mine) Purple else Color.White).padding(12.dp)
        ) {
            m.image?.let {
                Image(
                    it.asImageBitmap(), null,
                    Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(m.text, color = if (mine) Color.White else Color(0xFF1B1B1F), fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun SettingsDialog(p: Prefs, close: () -> Unit) {
    var key by remember { mutableStateOf(p.apiKey) }
    var model by remember { mutableStateOf(p.model) }
    var speak by remember { mutableStateOf(p.autoSpeak) }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = { p.apiKey = key; p.model = model; p.autoSpeak = speak; close() }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = close) { Text("بستن") } },
        title = { Text("تنظیمات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(key, { key = it }, label = { Text("کلید API (Anthropic)") }, singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("نام مدل") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("خواندن جواب با صدا", Modifier.weight(1f)); Switch(speak, { speak = it })
                }
                Text("کلید فقط روی همین گوشی در حافظهٔ خصوصی برنامه می‌ماند.", fontSize = 12.sp)
            }
        }
    )
}
