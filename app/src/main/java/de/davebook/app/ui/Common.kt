@file:OptIn(ExperimentalMaterial3Api::class)

package de.davebook.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davebook.app.AppState
import de.davebook.app.Person
import de.davebook.app.Store
import de.davebook.app.Sync
import de.davebook.app.SyncService
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

// ------------------------------------------------------------------ Herz

fun heartPath(size: Size): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(w * 0.5f, h * 0.27f)
        cubicTo(w * 0.5f, h * 0.05f, w * 0.02f, h * 0.02f, w * 0.02f, h * 0.36f)
        cubicTo(w * 0.02f, h * 0.62f, w * 0.32f, h * 0.78f, w * 0.5f, h * 0.95f)
        cubicTo(w * 0.68f, h * 0.78f, w * 0.98f, h * 0.62f, w * 0.98f, h * 0.36f)
        cubicTo(w * 0.98f, h * 0.02f, w * 0.5f, h * 0.05f, w * 0.5f, h * 0.27f)
        close()
    }
}

@Composable
fun HeartShape(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = heartPath(size)
        translate(top = size.height * 0.035f) {
            drawPath(path, color = Color(0x2A6B0030))
        }
        drawPath(
            path,
            brush = Brush.linearGradient(
                colors = listOf(HeartPink, HeartDeep),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.28f),
            radius = size.minDimension * 0.08f,
            center = Offset(size.width * 0.27f, size.height * 0.3f),
        )
    }
}

@Composable
fun PulsingHeart(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    HeartShape(modifier.graphicsLayer {
        scaleX = pulse
        scaleY = pulse
    })
}

private data class Particle(
    val id: Long,
    val dx: Float,
    val rise: Float,
    val size: Float,
    val duration: Int,
    val emoji: String,
)

/** Das große Herz auf der Startseite. [burstKey] erhöhen = Herzchen-Regen (z. B. bei eingehendem Herz). */
@Composable
fun HeartButton(onTap: () -> Unit, burstKey: Int, modifier: Modifier = Modifier) {
    val particles = remember { mutableStateListOf<Particle>() }
    val tapScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val infinite = rememberInfiniteTransition(label = "heartPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heartPulse",
    )
    val emojis = remember { listOf("❤️", "💗", "💕", "💖", "💘") }

    fun burst(n: Int) {
        val base = System.nanoTime()
        repeat(n) { i ->
            particles.add(
                Particle(
                    id = base + i,
                    dx = Random.nextInt(-120, 121).toFloat(),
                    rise = Random.nextInt(150, 290).toFloat(),
                    size = Random.nextInt(18, 34).toFloat(),
                    duration = Random.nextInt(900, 1600),
                    emoji = emojis.random(),
                ),
            )
        }
    }

    LaunchedEffect(burstKey) {
        if (burstKey > 0) burst(12)
    }

    Box(modifier.size(280.dp), contentAlignment = Alignment.Center) {
        HeartShape(
            Modifier
                .size(210.dp)
                .graphicsLayer {
                    val s = pulse * tapScale.value
                    scaleX = s
                    scaleY = s
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    burst(7)
                    scope.launch {
                        tapScale.snapTo(0.8f)
                        tapScale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 320f))
                    }
                    onTap()
                },
        )
        particles.toList().forEach { p ->
            key(p.id) {
                FloatingParticle(p) { particles.remove(p) }
            }
        }
    }
}

@Composable
private fun FloatingParticle(p: Particle, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(p.duration, easing = LinearOutSlowInEasing))
        onDone()
    }
    Text(
        p.emoji,
        fontSize = p.size.sp,
        modifier = Modifier.graphicsLayer {
            val v = t.value
            translationX = p.dx * v * density
            translationY = -p.rise * v * density
            alpha = 1f - v
            scaleX = 0.6f + 0.6f * v
            scaleY = 0.6f + 0.6f * v
        },
    )
}

// ------------------------------------------------------------------ Koppeln

/** Zeigt den 6-stelligen Code an, den das andere Handy eintippt. */
@Composable
fun PairCodeDisplay(state: AppState, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    val partner = state.me?.partner ?: Person.DAVID
    val context = LocalContext.current
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        error = false
        val s = Store.state.value
        val existing = s.pairCode
        code = if (existing != null && s.secret != null && s.pairCodeExpires > System.currentTimeMillis()) {
            existing
        } else {
            try {
                Sync.createPairCode()
            } catch (e: Exception) {
                error = true
                null
            }
        }
        if (code != null) SyncService.start(context)
    }

    val current = code
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when {
            error -> {
                Text("Keine Internetverbindung 😕", color = textColor, textAlign = TextAlign.Center)
                TextButton(onClick = { attempt++ }) { Text("Nochmal versuchen") }
            }

            current == null -> CircularProgressIndicator()

            else -> {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 2.dp) {
                    Text(
                        current.chunked(3).joinToString(" "),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        color = SetupInk,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (state.partnerConnected) {
                    Text(
                        "✅ ${partner.display} ist verbunden!",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Warte auf ${partner.display} …", color = textColor)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Der Code gilt 11 Stunden.",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** Eingabe des Codes vom anderen Handy. */
@Composable
fun PairJoinForm(onSuccess: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = code,
            onValueChange = { v ->
                code = v.filter { it.isDigit() }.take(6)
                error = null
            },
            label = { Text("6-stelliger Code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 28.sp,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        val err = error
        if (err != null) {
            Spacer(Modifier.height(8.dp))
            Text(err, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    val result = try {
                        if (Sync.joinWithCode(code)) null else "Code nicht gefunden. Stimmt er? Codes gelten 11 Stunden."
                    } catch (e: Exception) {
                        "Keine Internetverbindung 😕"
                    }
                    busy = false
                    if (result == null) onSuccess() else error = result
                }
            },
            enabled = code.length == 6 && !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Verbinden", fontSize = 17.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ Hilfen

@SuppressLint("BatteryLife")
fun openBatterySettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        )
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e2: Exception) {
            // nichts zu machen
        }
    }
}

fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    } catch (e: Exception) {
        // nichts zu machen
    }
}

/** "heute", "gestern" oder "am 12.10." */
fun dayLabel(ts: Long): String {
    val d = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> "heute"
        today.minusDays(1) -> "gestern"
        else -> "am " + d.format(DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN))
    }
}

fun startOfTodayMillis(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
fun SectionGap() = Spacer(Modifier.height(16.dp))

val CardShape = RoundedCornerShape(24.dp)
