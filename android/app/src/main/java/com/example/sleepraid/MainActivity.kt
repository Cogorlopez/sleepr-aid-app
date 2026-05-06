package com.example.sleepraid

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.content.res.Configuration
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.sleepraid.ui.theme.SleeprAidTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var noiseService by mutableStateOf<NoiseService?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            noiseService = (binder as NoiseService.NoiseBinder).getService()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            noiseService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        volumeControlStream = AudioManager.STREAM_MUSIC

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        bindService(Intent(this, NoiseService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            SleeprAidTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F0F0F)
                ) {
                    SleeprAidScreen(noiseService)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
    }
}

@Composable
fun SleeprAidScreen(service: NoiseService?) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var volume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }

    // isOn and selectedType are driven by service state (mutableStateOf in NoiseService),
    // so Compose recomposes automatically when the notification toggle changes them.
    val isOn = service?.isPlaying ?: false
    val selectedType = service?.noiseType ?: NoiseGenerator.NoiseType.PINK
    val pauseOtherAudio = service?.pauseOtherAudio ?: false
    var selectedPanel by remember { mutableStateOf(Panel.BASIC) }

    // Keep slider in sync when hardware volume buttons are pressed
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val prefs = remember { AppPreferences(context) }
    val savedTimerHours by prefs.timerHours.collectAsState(initial = 0)
    val savedTimerMinutes by prefs.timerMinutes.collectAsState(initial = 30)
    val savedWakeHours by prefs.wakeTimerHours.collectAsState(initial = 0)
    val savedWakeMinutes by prefs.wakeTimerMinutes.collectAsState(initial = 30)
    val scope = rememberCoroutineScope()

    val onPowerClick = {
        if (service != null) {
            if (isOn) service.stopPlayback() else service.play(selectedType)
        }
    }
    val onVolumeChange = { v: Float ->
        volume = v
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVolume).roundToInt(), 0)
    }
    val onTypeSelected: (NoiseGenerator.NoiseType) -> Unit = { type -> service?.updateNoiseType(type) }
    val onTimerSave: (Int, Int) -> Unit = { h, m ->
        scope.launch { prefs.saveTimerDuration(h, m) }
    }
    val onWakeTimerSave: (Int, Int) -> Unit = { h, m ->
        scope.launch { prefs.saveWakeTimerDuration(h, m) }
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            PowerButton(isOn = isOn, size = 160.dp, onClick = onPowerClick)

            Spacer(modifier = Modifier.width(32.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sleepr Aid",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(20.dp))
                VolumeControl(volume = volume, onVolumeChange = onVolumeChange)
                Spacer(modifier = Modifier.height(16.dp))
                PanelSwitcher(selected = selectedPanel, onSelect = { selectedPanel = it })
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedContent(
                    targetState = selectedPanel,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "panel"
                ) { panel ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        when (panel) {
                            Panel.BASIC -> {
                                SoundSelector(selectedType = selectedType, onTypeSelected = onTypeSelected)
                                Spacer(modifier = Modifier.height(12.dp))
                                SleepTimerControl(
                                    service = service,
                                    selectedType = selectedType,
                                    initialHours = savedTimerHours,
                                    initialMinutes = savedTimerMinutes,
                                    onSave = onTimerSave
                                )
                            }
                            Panel.ADVANCED -> {
                                WakeTimerControl(
                                    service = service,
                                    selectedType = selectedType,
                                    initialHours = savedWakeHours,
                                    initialMinutes = savedWakeMinutes,
                                    onSave = onWakeTimerSave
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                PauseAudioToggle(
                                    checked = pauseOtherAudio,
                                    selectedType = selectedType,
                                    onCheckedChange = { service?.updatePauseOtherAudio(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "Sleepr Aid",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PowerButton(isOn = isOn, size = 260.dp, onClick = onPowerClick)

            Spacer(modifier = Modifier.weight(0.4f))

            VolumeControl(volume = volume, onVolumeChange = onVolumeChange)

            Spacer(modifier = Modifier.height(24.dp))

            PanelSwitcher(selected = selectedPanel, onSelect = { selectedPanel = it })

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = selectedPanel,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "panel"
            ) { panel ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (panel) {
                        Panel.BASIC -> {
                            SoundSelector(selectedType = selectedType, onTypeSelected = onTypeSelected)
                            Spacer(modifier = Modifier.height(12.dp))
                            SleepTimerControl(
                                service = service,
                                selectedType = selectedType,
                                initialHours = savedTimerHours,
                                initialMinutes = savedTimerMinutes,
                                onSave = onTimerSave
                            )
                        }
                        Panel.ADVANCED -> {
                            WakeTimerControl(
                                service = service,
                                selectedType = selectedType,
                                initialHours = savedWakeHours,
                                initialMinutes = savedWakeMinutes,
                                onSave = onWakeTimerSave
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PauseAudioToggle(
                                checked = pauseOtherAudio,
                                selectedType = selectedType,
                                onCheckedChange = { service?.updatePauseOtherAudio(it) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun PowerButton(isOn: Boolean, size: Dp = 260.dp, onClick: () -> Unit) {
    val accentColor = if (isOn) Color(0xFF90CAF9) else Color(0xFF333333)
    val outerCircleColor = Color(0xFF1A1A1A)
    val innerCircleColor = Color(0xFF121212)
    val innerSize = size * (180f / 260f)
    val iconSize = size * (100f / 260f)

    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (isOn) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent),
                                radius = this.size.minDimension * 0.75f
                            )
                        )
                    }
                } else Modifier
            )
            .clip(CircleShape)
            .background(outerCircleColor)
            .border(
                width = 1.dp,
                color = if (isOn) accentColor.copy(alpha = 0.5f) else Color(0xFF252525),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(CircleShape)
                .background(innerCircleColor)
                .border(
                    width = 2.dp,
                    color = if (isOn) accentColor.copy(alpha = 0.2f) else Color(0xFF0A0A0A),
                    shape = CircleShape
                )
        )

        Icon(
            imageVector = Icons.Default.PowerSettingsNew,
            contentDescription = "Power",
            modifier = Modifier.size(iconSize),
            tint = accentColor
        )
    }
}

@Composable
fun VolumeControl(volume: Float, onVolumeChange: (Float) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "${(volume * 100).toInt()}%",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
            
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF90CAF9),
                    activeTrackColor = Color(0xFF90CAF9),
                    inactiveTrackColor = Color(0xFF2A2A2A)
                )
            )
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private enum class Panel { BASIC, ADVANCED }

@Composable
private fun PanelSwitcher(selected: Panel, onSelect: (Panel) -> Unit) {
    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Panel.entries.forEach { panel ->
                val isSelected = panel == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF90CAF9) else Color.Transparent)
                        .clickable { onSelect(panel) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (panel == Panel.BASIC) "Basic" else "Advanced",
                        color = if (isSelected) Color(0xFF0F0F0F) else Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

private val noiseTypeLabels = mapOf(
    NoiseGenerator.NoiseType.PINK to "Pink Noise",
    NoiseGenerator.NoiseType.WHITE to "White Noise",
    NoiseGenerator.NoiseType.BROWN to "Brown Noise"
)

@Composable
fun SoundSelector(
    selectedType: NoiseGenerator.NoiseType,
    onTypeSelected: (NoiseGenerator.NoiseType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = noiseTypeLabels[selectedType] ?: "",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
        ) {
            noiseTypeLabels.forEach { (type, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (type == selectedType) Color(0xFF90CAF9)
                                    else Color.White.copy(alpha = 0.9f),
                            fontSize = 16.sp
                        )
                    },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SleepTimerControl(
    service: NoiseService?,
    selectedType: NoiseGenerator.NoiseType,
    initialHours: Int,
    initialMinutes: Int,
    onSave: (Int, Int) -> Unit
) {
    val timerActive = service?.timerActive ?: false
    val remainingSeconds = service?.timerRemainingSeconds ?: 0

    var hours by remember(initialHours) { mutableIntStateOf(initialHours) }
    var minutes by remember(initialMinutes) { mutableIntStateOf(initialMinutes) }

    val noiseLabel = noiseTypeLabels[selectedType] ?: "Noise"

    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (timerActive) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = Color(0xFF90CAF9),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatRemainingTime(remainingSeconds),
                    color = Color(0xFF90CAF9),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { service?.cancelTimer() }) {
                    Text("Cancel", color = Color(0xFF90CAF9), fontSize = 14.sp)
                }
            } else {
                Text(
                    text = "$noiseLabel off in:",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TimerStepper(value = hours, label = "h", range = 0..12, step = 1) { hours = it }
                Spacer(modifier = Modifier.width(4.dp))
                TimerStepper(value = minutes, label = "m", range = 0..59, step = 5) { minutes = it }
                val canSet = hours > 0 || minutes > 0
                TextButton(
                    onClick = {
                        onSave(hours, minutes)
                        if (service?.isPlaying == false) service.play()
                        service?.startTimer(hours, minutes)
                    },
                    enabled = canSet
                ) {
                    Text(
                        "Set",
                        color = if (canSet) Color(0xFF90CAF9) else Color(0xFF555555),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun WakeTimerControl(
    service: NoiseService?,
    selectedType: NoiseGenerator.NoiseType,
    initialHours: Int,
    initialMinutes: Int,
    onSave: (Int, Int) -> Unit
) {
    val wakeTargetTime = service?.wakeTimerTargetTime
    val isWakeTimerActive = wakeTargetTime != null

    var hours by remember(initialHours) { mutableIntStateOf(initialHours) }
    var minutes by remember(initialMinutes) { mutableIntStateOf(initialMinutes) }

    val noiseLabel = noiseTypeLabels[selectedType] ?: "Noise"

    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isWakeTimerActive) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = Color(0xFF90CAF9),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Using a simple local countdown for the UI, or could just show target time.
                // For consistency with SleepTimer, let's show a countdown.
                var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(1000)
                        currentTime = System.currentTimeMillis()
                    }
                }
                val remainingSeconds = ((wakeTargetTime - currentTime) / 1000).coerceAtLeast(0).toInt()
                Text(
                    text = formatRemainingTime(remainingSeconds),
                    color = Color(0xFF90CAF9),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { service?.cancelWakeTimer() }) {
                    Text("Cancel", color = Color(0xFF90CAF9), fontSize = 14.sp)
                }
            } else {
                Text(
                    text = "$noiseLabel on in:",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TimerStepper(value = hours, label = "h", range = 0..12, step = 1) { hours = it }
                Spacer(modifier = Modifier.width(4.dp))
                TimerStepper(value = minutes, label = "m", range = 0..59, step = 5) { minutes = it }
                val canSet = hours > 0 || minutes > 0
                TextButton(
                    onClick = {
                        onSave(hours, minutes)
                        service?.scheduleWakeTimer(hours, minutes)
                    },
                    enabled = canSet && service?.isPlaying == false
                ) {
                    Text(
                        "Set",
                        color = if (canSet && service?.isPlaying == false) Color(0xFF90CAF9) else Color(0xFF555555),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerStepper(
    value: Int,
    label: String,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }

    // Sync text with stepper-driven value changes when the field isn't focused
    LaunchedEffect(value) {
        if (!focused) textValue = value.toString()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onValueChange((value - step).coerceAtLeast(range.first)) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        }
        BasicTextField(
            value = textValue,
            onValueChange = { input ->
                if (input.isEmpty() || (input.length <= 2 && input.all { it.isDigit() })) {
                    textValue = input
                    val parsed = input.toIntOrNull()
                    if (parsed != null && parsed in range) onValueChange(parsed)
                }
            },
            textStyle = LocalTextStyle.current.copy(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = SolidColor(Color(0xFF90CAF9)),
            modifier = Modifier
                .width(28.dp)
                .drawBehind {
                    if (focused) drawLine(
                        color = Color(0xFF90CAF9),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .onFocusChanged { state ->
                    if (focused && !state.isFocused) {
                        val clamped = textValue.toIntOrNull()?.coerceIn(range) ?: range.first
                        textValue = clamped.toString()
                        onValueChange(clamped)
                    }
                    focused = state.isFocused
                }
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        IconButton(
            onClick = { onValueChange((value + step).coerceAtMost(range.last)) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        }
    }
}

private fun formatRemainingTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

@Composable
fun PauseAudioToggle(
    checked: Boolean,
    selectedType: NoiseGenerator.NoiseType,
    onCheckedChange: (Boolean) -> Unit
) {
    val noiseLabel = noiseTypeLabels[selectedType] ?: "noise"
    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Pause other audio when $noiseLabel starts",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f).padding(end = 16.dp)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF90CAF9)
                )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SleeprAidPreview() {
    SleeprAidTheme(darkTheme = true, dynamicColor = false) {
        Surface(color = Color(0xFF0F0F0F)) {
            SleeprAidScreen(service = null)
        }
    }
}
