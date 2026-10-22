package com.offlinepay.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

const val LOCK_LEN = 4

object LockState {
    var locked by mutableStateOf(false)
}

@Composable
fun PinPad(pin: String, onChange: (String) -> Unit, enabled: Boolean = true, dark: Boolean = false) {
    val fg = if (dark) Color.White else Color(0xFF1C1B1F)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(LOCK_LEN) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (i < pin.length) fg else fg.copy(alpha = 0.25f))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<"))
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                r.forEach { k ->
                    Box(
                        Modifier.size(70.dp).clip(CircleShape)
                            .then(if (k.isNotEmpty()) Modifier.background(fg.copy(alpha = 0.10f)) else Modifier)
                            .then(
                                if (k.isNotEmpty() && enabled) Modifier.clickable {
                                    if (k == "<") onChange(pin.dropLast(1))
                                    else if (pin.length < LOCK_LEN) onChange(pin + k)
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (k) {
                            "" -> {}
                            "<" -> Icon(Icons.Rounded.Backspace, null, tint = fg)
                            else -> Text(k, fontSize = 26.sp, color = fg, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/** Full-screen lock drawn ON TOP of the app so an in-progress payment is never interrupted. */
@Composable
fun LockOverlay() {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    var until by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(until) {
        while (System.currentTimeMillis() < until) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val waitSec = ((until - now) / 1000 + 1).coerceAtLeast(0)
    val blocked = now < until

    LaunchedEffect(pin) {
        if (pin.length == LOCK_LEN) {
            if (Prefs.verifyPin(ctx, pin)) {
                LockState.locked = false
            } else {
                error = true
                wrong++
                if (wrong >= 5) {
                    wrong = 0
                    until = System.currentTimeMillis() + 30_000
                    now = System.currentTimeMillis()
                }
                delay(250)
                pin = ""
            }
        } else if (pin.isNotEmpty()) error = false
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PurpleDark, Purple)))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text("OfflinePay", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    blocked -> tr("Bahut galat PIN. $waitSec second ruken.", "बहुत गलत PIN। $waitSec सेकंड रुकें।")
                    error -> tr("PIN galat hai", "PIN गलत है")
                    else -> tr("Apna app PIN daalein", "अपना ऐप PIN डालें")
                },
                color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp
            )
            Spacer(Modifier.height(28.dp))
            PinPad(pin, { pin = it }, enabled = !blocked, dark = true)
        }
    }
}

/** Set a new lock PIN (enter twice). */
@Composable
fun PinSetupScreen(onSaved: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    LaunchedEffect(pin) {
        if (pin.length == LOCK_LEN) {
            val f = first
            if (f == null) {
                first = pin; pin = ""; mismatch = false
            } else if (f == pin) {
                Prefs.setLockPin(ctx, pin)
                onSaved()
            } else {
                mismatch = true; first = null; delay(250); pin = ""
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BackRow(tr("App Lock", "ऐप लॉक"), onBack)
        Spacer(Modifier.height(24.dp))
        IconBadge(Icons.Rounded.Lock, 64.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            if (first == null) tr("Naya $LOCK_LEN-ank ka PIN banayein", "नया $LOCK_LEN-अंक का PIN बनाएं")
            else tr("PIN dobara daalein", "PIN दोबारा डालें"),
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        if (mismatch) Text(tr("PIN match nahi hue, dobara banayein", "PIN मैच नहीं हुए, दोबारा बनाएं"), color = Red, fontSize = 13.sp)
        Spacer(Modifier.height(26.dp))
        PinPad(pin, { pin = it })
        Spacer(Modifier.height(20.dp))
        Text(
            tr("Yeh PIN bhoolne par app ka data clear karna padega (history bhi hat jayegi). Isliye yaad rakhein.",
                "यह PIN भूलने पर ऐप का डेटा क्लियर करना पड़ेगा (हिस्ट्री भी हट जाएगी)। इसलिए याद रखें।"),
            fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center
        )
    }
}

/** Ask for the current PIN before turning App Lock off. */
@Composable
fun PinDisableScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(pin) {
        if (pin.length == LOCK_LEN) {
            if (Prefs.verifyPin(ctx, pin)) {
                Prefs.setLockPin(ctx, null)
                onDone()
            } else {
                error = true; delay(250); pin = ""
            }
        } else if (pin.isNotEmpty()) error = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BackRow(tr("App Lock band karein", "ऐप लॉक बंद करें"), onBack)
        Spacer(Modifier.height(24.dp))
        IconBadge(Icons.Rounded.LockOpen, 64.dp)
        Spacer(Modifier.height(14.dp))
        Text(tr("Abhi ka PIN daalein", "अभी का PIN डालें"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (error) Text(tr("PIN galat hai", "PIN गलत है"), color = Red, fontSize = 13.sp)
        Spacer(Modifier.height(26.dp))
        PinPad(pin, { pin = it })
    }
}
