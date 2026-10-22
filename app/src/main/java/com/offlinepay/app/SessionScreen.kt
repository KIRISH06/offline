package com.offlinepay.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SessionScreen(job: UssdJob, subtitle: String, onClose: () -> Unit, onReceipt: (Long) -> Unit) {
    val ctx = LocalContext.current
    val session = remember { PaySession(ctx, job) }
    LaunchedEffect(Unit) { session.start() }
    val close = { session.cancel(); onClose() }
    val state = session.state
    val canLeave = session.canLeave
    BackHandler { if (canLeave) close() }

    val txnId = (job as? UssdJob.Pay)?.txnId
    val amount = (job as? UssdJob.Pay)?.amount

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state is SState.Done || canLeave) {
            BackRow(
                if (job is UssdJob.Balance) tr("Balance", "बैलेंस") else tr("Payment", "पेमेंट"),
                if (state is SState.Done) onClose else close
            )
        } else {
            Spacer(Modifier.height(24.dp))
        }

        when (state) {
            is SState.Working -> LoadingPanel(state.stage, amount, subtitle, job is UssdJob.Balance)

            is SState.NeedPin -> {
                if (amount != null) AmountHeader(amount, subtitle)
                PinPanel(state.bankMsg, state.receiver, onSubmit = { session.submitPin(it) }, onCancel = close)
            }

            is SState.Manual -> {
                if (amount != null) AmountHeader(amount, subtitle)
                ManualPanel(state.msg, onReply = { session.reply(it) }, onClose = close)
            }

            is SState.Done -> ResultPanel(
                ok = state.ok, msg = state.msg, txnId = txnId, isBalance = job is UssdJob.Balance,
                onClose = onClose, onReceipt = onReceipt
            )
        }
    }
}

@Composable
private fun AmountHeader(amount: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("₹$amount", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------- loading
@Composable
private fun LoadingPanel(stage: Int, amount: String?, subtitle: String, balance: Boolean) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val p1 by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p1"
    )
    val p2 by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart, StartOffset(900)),
        label = "p2"
    )

    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (amount != null) {
            Text("₹$amount", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            listOf(p1, p2).forEach { p ->
                Box(
                    Modifier.size(120.dp)
                        .graphicsLayer { scaleX = 0.8f + p * 0.9f; scaleY = 0.8f + p * 0.9f; alpha = (1f - p) * 0.35f }
                        .clip(CircleShape).background(Purple)
                )
            }
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Purple),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (balance) Icons.Rounded.AccountBalanceWallet else Icons.Rounded.Lock,
                    null, tint = Color.White, modifier = Modifier.size(44.dp)
                )
            }
        }
        Text(
            if (balance) tr("Balance check ho raha hai", "बैलेंस चेक हो रहा है")
            else tr("Payment process ho raha hai", "पेमेंट प्रोसेस हो रहा है"),
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StageRow(tr("Bank se connect", "बैंक से कनेक्ट"), 0, stage)
                StageRow(tr("Receiver verify", "रिसीवर वेरीफाई"), 1, stage)
                StageRow(tr("Payment process", "पेमेंट प्रोसेस"), 2, stage)
            }
        }
        Text(
            tr("Kripya app band na karein, payment pura hone tak ruke.",
                "कृपया ऐप बंद न करें, पेमेंट पूरा होने तक रुकें।"),
            fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StageRow(label: String, index: Int, current: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when {
                index < current -> Icon(Icons.Rounded.CheckCircle, null, tint = Green)
                index == current -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = Purple
                )
                else -> Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD6D0DE)))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label, fontSize = 15.sp,
            fontWeight = if (index == current) FontWeight.SemiBold else FontWeight.Normal,
            color = if (index > current) Color.Gray else Color(0xFF1C1B1F)
        )
    }
}

// ---------------------------------------------------------------- PIN
@Composable
private fun PinPanel(bankMsg: String, receiver: String, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }

    if (receiver.isNotBlank()) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Verified, 44.dp, tint = Green, bg = Color.White)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(tr("Receiver (bank ke anusaar)", "रिसीवर (बैंक के अनुसार)"), fontSize = 12.sp, color = Color.DarkGray)
                    Text(receiver, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))) {
            Row(Modifier.padding(14.dp)) {
                Icon(Icons.Rounded.Info, null, tint = Color(0xFF8A6D00), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    tr("Bank ne receiver ka naam alag se nahi bheja. Neeche bank ka sandesh padhkar jaanch lein.",
                        "बैंक ने रिसीवर का नाम अलग से नहीं भेजा। नीचे बैंक का संदेश पढ़कर जाँच लें।"),
                    fontSize = 13.sp
                )
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(tr("Bank ka sandesh", "बैंक का संदेश"), fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Text(bankMsg, fontSize = 14.sp)
        }
    }
    OutlinedTextField(
        value = pin,
        onValueChange = { pin = it.filter(Char::isDigit).take(6) },
        label = { Text("UPI PIN") },
        leadingIcon = { Icon(Icons.Rounded.Lock, null) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { val p = pin; pin = ""; onSubmit(p) },
        enabled = pin.length in 4..6,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Green)
    ) { Text(tr("Payment confirm karein", "पेमेंट कन्फ़र्म करें"), fontSize = 16.sp) }
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(tr("Rokein", "रोकें"), color = Red)
    }
    Text(
        tr("PIN seedha bank ko jaata hai. App ise save ya record nahi karta.",
            "PIN सीधे बैंक को जाता है। ऐप इसे सेव या रिकॉर्ड नहीं करता।"),
        fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ManualPanel(msg: String, onReply: (String) -> Unit, onClose: () -> Unit) {
    var text by remember(msg) { mutableStateOf("") }
    val opts = remember(msg) { UssdFlow.options(msg) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(msg, Modifier.fillMaxWidth().padding(16.dp), fontSize = 15.sp)
    }
    if (opts.isNotEmpty()) {
        opts.forEach { (num, label) ->
            OutlinedButton(onClick = { onReply(num) }, modifier = Modifier.fillMaxWidth()) { Text("$num. $label") }
        }
    } else {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            label = { Text(tr("Jawab likhein", "जवाब लिखें")) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { val t = text; text = ""; onReply(t) },
            enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text(tr("Bhejein", "भेजें")) }
    }
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(tr("Band karein", "बंद करें")) }
}

// ---------------------------------------------------------------- result
@Composable
private fun ResultPanel(
    ok: Boolean, msg: String, txnId: Long?, isBalance: Boolean,
    onClose: () -> Unit, onReceipt: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val txn = remember(txnId) { txnId?.let { TxnStore(ctx).find(it) } }
    val color = if (ok) Green else Red

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        if (shown) 1f else 0.2f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale"
    )
    val fade by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "fade")

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(120.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = fade }
                .clip(CircleShape).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(
                    if (ok) Icons.Rounded.Check else Icons.Rounded.Close,
                    null, tint = Color.White, modifier = Modifier.size(52.dp)
                )
            }
        }
        Text(
            when {
                isBalance && ok -> tr("Balance mil gaya", "बैलेंस मिल गया")
                ok -> tr("Payment safal", "पेमेंट सफल")
                else -> tr("Payment nahi hui", "पेमेंट नहीं हुई")
            },
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color
        )
        if (txn != null) {
            Text("₹${txn.amount}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KV(tr("Receiver", "रिसीवर"), txn.receiver.ifBlank { txn.payee })
                    KV(tr("Payment ID", "पेमेंट ID"), txn.payId)
                    if (txn.bankRef.isNotBlank()) KV(tr("Bank reference", "बैंक रेफरेंस"), txn.bankRef)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Text(msg, Modifier.fillMaxWidth().padding(16.dp), fontSize = 14.sp)
        }
        if (txnId != null) {
            Button(
                onClick = { onReceipt(txnId) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Receipt dekhein", "रसीद देखें"))
            }
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(tr("Done", "ठीक है"))
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    Column {
        Text(k, fontSize = 12.sp, color = Color.Gray)
        Text(v, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
