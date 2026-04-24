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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    var volume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }

    // isOn and selectedType are driven by service state (mutableStateOf in NoiseService),
    // so Compose recomposes automatically when the notification toggle changes them.
    val isOn = service?.isPlaying ?: false
    val selectedType = service?.noiseType ?: NoiseGenerator.NoiseType.PINK

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .statusBarsPadding()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Title
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

        // Power Button
        PowerButton(isOn = isOn, onClick = {
            service ?: return@PowerButton
            if (isOn) service.stopPlayback() else service.play(selectedType)
        })

        Spacer(modifier = Modifier.weight(1.2f))

        // Volume Control
        VolumeControl(volume = volume, onVolumeChange = {
            volume = it
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).roundToInt(), 0)
        })

        Spacer(modifier = Modifier.height(40.dp))

        // Sound Selector
        SoundSelector(
            selectedType = selectedType,
            onTypeSelected = { type -> service?.updateNoiseType(type) }
        )

        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
fun PowerButton(isOn: Boolean, onClick: () -> Unit) {
    val accentColor = if (isOn) Color(0xFF90CAF9) else Color(0xFF333333)
    val outerCircleColor = Color(0xFF1A1A1A)
    val innerCircleColor = Color(0xFF121212)
    
    Box(
        modifier = Modifier
            .size(260.dp)
            .then(
                if (isOn) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent),
                                radius = size.minDimension * 0.75f
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
        // Inner depth circle
        Box(
            modifier = Modifier
                .size(180.dp)
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
            modifier = Modifier.size(100.dp),
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SleeprAidPreview() {
    SleeprAidTheme(darkTheme = true, dynamicColor = false) {
        Surface(color = Color(0xFF0F0F0F)) {
            SleeprAidScreen(service = null)
        }
    }
}
