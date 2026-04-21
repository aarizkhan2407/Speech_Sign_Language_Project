package com.example.speech_sign_language_project

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import android.graphics.drawable.Animatable
import androidx.compose.foundation.Image

@Composable
fun PlaybackControls(
    isPaused: Boolean,
    isLooping: Boolean,
    onTogglePlayPause: () -> Unit,
    onReplay: () -> Unit,
    onToggleLoop: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(8.dp)
            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onReplay) {
            Icon(Icons.Default.Replay, contentDescription = "Replay", tint = Color(0xFF64748B))
        }
        
        FloatingActionButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(48.dp),
            containerColor = Color(0xFF3B82F6),
            contentColor = Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(0.dp)
        ) {
            Icon(
                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Play" else "Pause"
            )
        }
        
        IconButton(onClick = onToggleLoop) {
            Icon(
                Icons.Default.Loop,
                contentDescription = "Loop",
                tint = if (isLooping) Color(0xFF3B82F6) else Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun SignPreview(
    sequence: List<SignItem>,
    currentIndex: Int,
    isPaused: Boolean,
    imageLoader: ImageLoader,
    playbackSessionKey: Int
) {
    if (sequence.isEmpty()) {
        Text("Voice recognized signs will appear here", color = Color(0xFF94A3B8), fontSize = 14.sp)
    } else {
        val currentSign = sequence.getOrNull(currentIndex)
        if (currentSign != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Key the painter with session key and current index to force refresh for same signs
                key(playbackSessionKey, currentIndex) {
                    val painter = rememberAsyncImagePainter(
                        model = "file:///android_asset/${currentSign.assetPath}",
                        imageLoader = imageLoader
                    )
                    
                    Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painter,
                            contentDescription = currentSign.name,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        if (painter.state is AsyncImagePainter.State.Loading) {
                            CircularProgressIndicator(color = Color(0xFF3B82F6))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    color = Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = currentSign.name.uppercase(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569),
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MicButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    volume: Float = 0f
) {
    val bgColor = if (isListening) Color(0xFFEF4444) else Color(0xFF3B82F6)
    val animatedVolume by animateFloatAsState(targetValue = volume, label = "volume")
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, label = "scale")

    Box(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isListening) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barCount = 5
                val barWidth = 4.dp.toPx()
                val spacing = 6.dp.toPx()
                val totalWidth = (barCount * barWidth) + ((barCount - 1) * spacing)
                val startX = (size.width - totalWidth) / 2
                
                for (i in 0 until barCount) {
                    val reaction = (animatedVolume + 2f).coerceIn(1f, 15f)
                    val heightFactor = when(i) {
                        0, 4 -> 0.4f
                        1, 3 -> 0.7f
                        else -> 1.0f
                    }
                    val barHeight = (size.height * 0.2f) + (size.height * 0.4f * (reaction / 15f) * heightFactor)
                    
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.6f),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            x = startX + i * (barWidth + spacing),
                            y = (size.height - barHeight) / 2
                        ),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
                    )
                }
            }
        }
        
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun ListeningCard(status: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = status,
            color = Color(0xFF3B82F6),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun ModeButton(context: Context) {
    OutlinedButton(
        onClick = { context.startActivity(Intent(context, SignToSpeechActivity::class.java)) },
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF3B82F6)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF3B82F6))
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text("Switch to Sign Detection", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
fun RecognizedTextDisplay(text: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Recognized Text:", color = Color(0xFF64748B), fontSize = 12.sp)
        Text(
            text = text,
            color = Color(0xFF0F172A),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}
