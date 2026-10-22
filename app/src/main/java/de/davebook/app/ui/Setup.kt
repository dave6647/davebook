@file:OptIn(ExperimentalMaterial3Api::class)

package de.davebook.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davebook.app.AppState
import de.davebook.app.Person
import de.davebook.app.Store

private const val STEP_WELCOME = "welcome"
private const val STEP_LOVE = "love"
private const val STEP_SHOW = "show"
private const val STEP_JOIN = "join"

/**
 * Einrichtung beim ersten Start.
 * Sophie: Willkommen → "Wie sehr liebst du David?" → Code für David anzeigen.
 * David: tippt unten auf "Ich bin David" → Code von Sophie eingeben.
 */
@Composable
fun SetupFlow(state: AppState, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(STEP_WELCOME) }
    val background = Brush.verticalGradient(listOf(Color(0xFFFFF0F4), Color(0xFFFFC2D6)))

    Box(
        Modifier
            .fillMaxSize()
            .background(background),
    ) {
        CompositionLocalProvider(LocalContentColor provides SetupInk) {
            Crossfade(targetState = step, label = "setup") { s ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .imePadding(),
                ) {
                    when (s) {
                        STEP_WELCOME -> WelcomeStep(
                            onNext = {
                                Store.setMe(Person.SOPHIE)
                                step = STEP_LOVE
                            },
                            onDavid = {
                                Store.setMe(Person.DAVID)
                                step = STEP_JOIN
                            },
                        )

                        STEP_LOVE -> LoveStep(onNext = { v ->
                            Store.setLove(v)
                            step = STEP_SHOW
                        })

                        STEP_SHOW -> PairShowStep(
                            state = state,
                            onDone = onFinished,
                            onEnterInstead = { step = STEP_JOIN },
                        )

                        else -> PairJoinStep(
                            state = state,
                            onDone = onFinished,
                            onShowInstead = { step = STEP_SHOW },
                            onBack = { step = STEP_WELCOME },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit, onDavid: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        PulsingHeart(Modifier.size(170.dp))
        Spacer(Modifier.height(40.dp))
        Text(
            "Willkommen bei Davebook",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "mein Hase ❤️",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = HeartDeep,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Los geht's", fontSize = 18.sp)
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onDavid) {
            Text("Ich bin David", color = SetupInk.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun LoveStep(onNext: (String) -> Unit) {
    val options = listOf("Unfassbar", "Unendlich", "Unbeschreiblich")
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("💞", fontSize = 64.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Wie sehr liebst du David?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        Box(Modifier.fillMaxWidth()) {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(2.dp, if (expanded) HeartDeep else SetupInk.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val current = selected
                    Text(
                        current ?: "Bitte auswählen …",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (current == null) SetupInk.copy(alpha = 0.45f) else SetupInk,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = SetupInk)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .widthIn(min = 260.dp)
                    .background(Color.White),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(option, style = MaterialTheme.typography.titleMedium, color = SetupInk)
                        },
                        onClick = {
                            selected = option
                            expanded = false
                        },
                    )
                }
            }
        }

        val reaction = when (selected) {
            "Unfassbar" -> "Wusste ich's doch 🥰"
            "Unendlich" -> "Für immer und ewig ♾️"
            "Unbeschreiblich" -> "Dann zeig's ihm einfach jeden Tag 💕"
            else -> ""
        }
        Spacer(Modifier.height(20.dp))
        AnimatedVisibility(visible = reaction.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Text(
                reaction,
                style = MaterialTheme.typography.titleMedium,
                color = HeartDeep,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { selected?.let(onNext) },
            enabled = selected != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Weiter", fontSize = 18.sp)
        }
        Spacer(Modifier.height(54.dp))
    }
}

@Composable
private fun PairShowStep(state: AppState, onDone: () -> Unit, onEnterInstead: () -> Unit) {
    val partner = state.me?.partner ?: Person.DAVID
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("🔐", fontSize = 56.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            "Fast geschafft!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Zeig ${partner.display} diesen Code. ${partner.er.replaceFirstChar { it.uppercase() }} tippt ihn in " +
                "${partner.seiner} Davebook-App ein – dann seid ihr verbunden.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PairCodeDisplay(state, textColor = SetupInk)
        Spacer(Modifier.height(40.dp))
        if (state.partnerConnected) {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Los geht's ❤️", fontSize = 18.sp)
            }
        } else {
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Später verbinden", color = SetupInk)
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onEnterInstead) {
            Text("Ich habe einen Code von ${partner.display}", color = SetupInk.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun PairJoinStep(state: AppState, onDone: () -> Unit, onShowInstead: () -> Unit, onBack: () -> Unit) {
    val me = state.me ?: Person.DAVID
    val partner = me.partner
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(if (me == Person.DAVID) "👋" else "🔐", fontSize = 56.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            if (me == Person.DAVID) "Hey David!" else "Code eingeben",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Gib den Code ein, den ${partner.display}s Davebook-App gerade anzeigt.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PairJoinForm(onSuccess = onDone)
        Spacer(Modifier.height(40.dp))
        TextButton(onClick = onShowInstead) {
            Text("Stattdessen selbst einen Code anzeigen", color = SetupInk.copy(alpha = 0.55f))
        }
        if (me == Person.DAVID) {
            TextButton(onClick = onBack) {
                Text("Zurück", color = SetupInk.copy(alpha = 0.55f))
            }
        }
    }
}
