package com.offlinepay.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

val Purple = Color(0xFF5F259F)
val PurpleDark = Color(0xFF3B1470)
val Soft = Color(0xFFF0E8FA)
val Green = Color(0xFF1B9E5A)
val Red = Color(0xFFD93025)
val Amber = Color(0xFFF9A825)
const val MAX_AMOUNT = 5000L

@Composable
fun OfflinePayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Purple, secondary = Green,
            background = Color(0xFFF6F3FA), surface = Color.White
        ),
        content = content
    )
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

// ---------------------------------------------------------------- shared UI
@Composable
fun BackRow(title: String, onBack: () -> Unit, light: Boolean = false) {
    val c = if (light) Color.White else Color(0xFF1C1B1F)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.ArrowBack, contentDescription = null, tint = c,
            modifier = Modifier.clip(CircleShape).clickable { onBack() }.padding(10.dp).size(24.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(title, color = c, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun IconBadge(icon: ImageVector, boxSize: Dp = 46.dp, tint: Color = Purple, bg: Color = Soft) {
    Box(Modifier.size(boxSize).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(boxSize * 0.52f))
    }
}

@Composable
fun InfoNote(text: String, icon: ImageVector = Icons.Rounded.Info) {
    Card(colors = CardDefaults.cardColors(containerColor = Soft)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = Purple, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 12.sp, color = Color.DarkGray)
        }
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon)
            Column {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(sub, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun HeaderChip(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector, title: String, sub: String,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Color.Gray)
            }
            trailing?.invoke()
        }
    }
}

fun statusColor(status: String) = when (status) { "SUCCESS" -> Green; "FAILED" -> Red; else -> Amber }
fun statusIcon(status: String) = when (status) {
    "SUCCESS" -> Icons.Rounded.CheckCircle
    "FAILED" -> Icons.Rounded.Error
    else -> Icons.Rounded.Schedule
}

// ---------------------------------------------------------------- LOGIN (Google / Guest)
@Composable
fun LoginPanel(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                if (!GoogleAuth.configured) {
                    error = tr(
                        "Google login abhi is app mein setup nahi hai. Abhi Guest se chalayein.",
                        "Google लॉगिन अभी इस ऐप में सेटअप नहीं है। अभी Guest से चलाएं।"
                    )
                    return@Button
                }
                val act = ctx.findActivity() ?: return@Button
                busy = true; error = ""
                scope.launch {
                    val r = GoogleAuth.signIn(act)
                    busy = false
                    r.onSuccess { (name, email) ->
                        Prefs.setUser(ctx, "google", name, email)
                        onDone()
                    }.onFailure {
                        error = tr("Google login nahi hua. Dobara try karein ya Guest chunein.",
                            "Google लॉगिन नहीं हुआ। दोबारा कोशिश करें या Guest चुनें।")
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1C1B1F)),
            elevation = ButtonDefaults.buttonElevation(2.dp)
        ) {
            Icon(Icons.Rounded.AccountCircle, null, tint = Color(0xFF4285F4))
            Spacer(Modifier.width(10.dp))
            Text(tr("Google se login karein", "Google से लॉगिन करें"), fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = { Prefs.setUser(ctx, "guest", "", ""); onDone() },
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Icon(Icons.Rounded.Person, null)
            Spacer(Modifier.width(10.dp))
            Text(tr("Guest ke roop mein chalayein", "Guest के रूप में चलाएं"), fontWeight = FontWeight.SemiBold)
        }
        if (error.isNotBlank()) Text(error, color = Red, fontSize = 13.sp)
    }
}

@Composable
fun LoginScreen(onDone: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BackRow(tr("Account", "अकाउंट"), onBack)
        InfoNote(tr("Login sirf aapki pehchaan ke liye hai. Payment ke liye bank PIN hi lagta hai.",
            "लॉगिन सिर्फ़ आपकी पहचान के लिए है। पेमेंट के लिए बैंक PIN ही लगता है।"))
        LoginPanel(onDone)
    }
}

// ---------------------------------------------------------------- SIM PICKER
@Composable
fun SimPickerPanel(confirmLabel: String, onConfirm: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val sims = remember(tick) { SimManager.active(ctx) }
    var sel by remember(sims) {
        mutableStateOf(
            sims.firstOrNull { it.subId == Prefs.simId(ctx) } ?: sims.singleOrNull()
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sims.isEmpty()) {
            InfoNote(tr("Koi active SIM nahi mili. SIM lagakar signal aane par Refresh dabayein.",
                "कोई एक्टिव SIM नहीं मिली। SIM लगाकर सिग्नल आने पर Refresh दबाएं।"), Icons.Rounded.Error)
            OutlinedButton(onClick = { tick++ }, modifier = Modifier.fillMaxWidth()) { Text("Refresh") }
        } else {
            if (sims.size > 1) {
                Text(tr("Kaun si SIM se payment karni hai?", "कौन सी SIM से पेमेंट करनी है?"), fontWeight = FontWeight.SemiBold)
            }
            sims.forEach { s ->
                val chosen = sel?.subId == s.subId
                Card(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { sel = s },
                    colors = CardDefaults.cardColors(containerColor = if (chosen) Soft else Color.White)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.SimCard, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("SIM ${s.slot}", fontWeight = FontWeight.SemiBold)
                            Text(s.carrier, fontSize = 13.sp, color = Color.Gray)
                        }
                        RadioButton(selected = chosen, onClick = { sel = s })
                    }
                }
            }
            InfoNote(tr("Ek baar chunein, app yaad rakhega. Baad mein Settings se badal sakte hain. Wahi SIM chunein jo bank mein registered hai.",
                "एक बार चुनें, ऐप याद रखेगा। बाद में सेटिंग्स से बदल सकते हैं। वही SIM चुनें जो बैंक में रजिस्टर्ड है।"))
        }
        Button(
            onClick = {
                sel?.let { Prefs.setSim(ctx, it.subId, it.label); onConfirm() }
            },
            enabled = sel != null,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(confirmLabel) }
    }
}

@Composable
fun SimPickScreen(onDone: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackRow(tr("Payment SIM", "पेमेंट SIM"), onBack)
        SimPickerPanel(tr("Save karein", "सेव करें"), onDone)
    }
}

// ---------------------------------------------------------------- HOME
@Composable
fun HomeScreen(go: (Screen) -> Unit, onBalance: () -> Unit) {
    val ctx = LocalContext.current
    val name = Prefs.userName(ctx)
    val google = Prefs.userType(ctx) == "google"
    val greet = if (google && name.isNotBlank()) tr("Namaste, $name", "नमस्ते, $name")
    else tr("Namaste, Guest", "नमस्ते, Guest")
    val sim = Prefs.simLabel(ctx)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(PurpleDark, Purple)),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                ).padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OfflinePay", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Security, null, tint = Color.White.copy(alpha = 0.85f))
                }
                Text(greet, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderChip(Icons.Rounded.SimCard, sim.ifBlank { "SIM" })
                    if (Prefs.lockEnabled(ctx)) HeaderChip(Icons.Rounded.Lock, tr("App Lock ON", "ऐप लॉक ON"))
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(Icons.Rounded.QrCodeScanner, tr("Scan & Pay", "स्कैन करें"),
                    tr("QR se pay", "QR से पे"), Modifier.weight(1f)) { go(Screen.Scan) }
                ActionTile(Icons.Rounded.AlternateEmail, "UPI ID",
                    tr("ID se pay", "ID से पे"), Modifier.weight(1f)) { go(Screen.Manual) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(Icons.Rounded.PhoneAndroid, tr("Mobile number", "मोबाइल नंबर"),
                    tr("Number se pay", "नंबर से पे"), Modifier.weight(1f)) { go(Screen.Mobile) }
                ActionTile(Icons.Rounded.AccountBalanceWallet, tr("Balance", "बैलेंस"),
                    tr("Bank balance", "बैंक बैलेंस"), Modifier.weight(1f)) { onBalance() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(Icons.Rounded.History, tr("History", "हिस्ट्री"),
                    tr("Payment proof", "पेमेंट प्रूफ"), Modifier.weight(1f)) { go(Screen.History) }
                ActionTile(Icons.Rounded.Settings, tr("Settings", "सेटिंग्स"),
                    tr("SIM, Lock, Account", "SIM, लॉक, अकाउंट"), Modifier.weight(1f)) { go(Screen.Settings) }
            }
            InfoNote(
                tr("Limit ₹$MAX_AMOUNT per transaction. Har transaction par ~₹0.50 telecom charge lag sakta hai. SIM mein signal zaroori.",
                    "सीमा ₹$MAX_AMOUNT प्रति लेनदेन। हर लेनदेन पर ~₹0.50 टेलीकॉम चार्ज लग सकता है। SIM में सिग्नल ज़रूरी।")
            )
        }
    }
}

// ---------------------------------------------------------------- UPI ID
@Composable
fun ManualScreen(onNext: (UpiPayee) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var vpa by remember { mutableStateOf("") }
    val recents = remember {
        TxnStore(ctx).all().filter { UpiParser.isValidVpa(it.vpa) }.distinctBy { it.vpa }.take(5)
    }
    val valid = UpiParser.isValidVpa(vpa)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackRow(tr("UPI ID se pay", "UPI ID से पे"), onBack)
        OutlinedTextField(
            value = vpa, onValueChange = { vpa = it.trim().lowercase() },
            label = { Text("UPI ID (name@bank)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            isError = vpa.isNotEmpty() && !valid
        )
        InfoNote(
            tr("PIN se pehle bank us UPI ID se jude bank account ka naam dikhayega. Wahi payment ka proof hai. Naam sahi ho tabhi PIN daalein.",
                "PIN से पहले बैंक उस UPI ID से जुड़े बैंक अकाउंट का नाम दिखाएगा। वही पेमेंट का प्रूफ है। नाम सही हो तभी PIN डालें।"),
            Icons.Rounded.Verified
        )
        if (recents.isNotEmpty()) {
            Text(tr("Recent", "हाल के"), fontWeight = FontWeight.SemiBold)
            recents.forEach {
                Text(
                    "${it.receiver.ifBlank { it.payee }}  •  ${it.vpa}",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color.White).clickable { vpa = it.vpa }.padding(14.dp),
                    fontSize = 14.sp
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onNext(UpiPayee(vpa, vpa)) }, enabled = valid,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(tr("Aage badhein", "आगे बढ़ें")) }
    }
}

// ---------------------------------------------------------------- MOBILE NUMBER
@Composable
fun MobileScreen(onNext: (UpiPayee) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var num by remember { mutableStateOf("") }
    val mobileRe = remember { Regex("^[6-9]\\d{9}$") }
    val valid = mobileRe.matches(num)
    val recents = remember {
        TxnStore(ctx).all().filter { mobileRe.matches(it.vpa) }.distinctBy { it.vpa }.take(5)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackRow(tr("Mobile number se pay", "मोबाइल नंबर से पे"), onBack)
        OutlinedTextField(
            value = num, onValueChange = { num = it.filter(Char::isDigit).take(10) },
            label = { Text(tr("10 digit mobile number", "10 अंकों का मोबाइल नंबर")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            isError = num.length == 10 && !valid,
            supportingText = { Text(tr("UPI ID bank khud dhoondh leta hai", "UPI ID बैंक खुद ढूँढ लेता है")) }
        )
        InfoNote(
            tr("Paise tabhi jayenge jab us number par UPI chalu ho. Bank PIN se pehle receiver ka naam dikhayega. Naam sahi ho tabhi PIN daalein. UPI chalu na ho to payment hogi hi nahi aur paise nahi katenge.",
                "पैसे तभी जाएंगे जब उस नंबर पर UPI चालू हो। बैंक PIN से पहले रिसीवर का नाम दिखाएगा। नाम सही हो तभी PIN डालें। UPI चालू न हो तो पेमेंट होगा ही नहीं और पैसे नहीं कटेंगे।"),
            Icons.Rounded.Verified
        )
        recents.forEach {
            Text(
                "${it.vpa}  •  ${it.receiver.ifBlank { it.payee }}",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color.White).clickable { num = it.vpa }.padding(14.dp),
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onNext(UpiPayee(num, tr("Mobile number", "मोबाइल नंबर"), mobile = true)) },
            enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(tr("Aage badhein", "आगे बढ़ें")) }
    }
}

// ---------------------------------------------------------------- PAY
@Composable
fun PayScreen(payee: UpiPayee, onBack: () -> Unit, onSession: (UssdJob, String) -> Unit) {
    val ctx = LocalContext.current
    val qrAmount = payee.amount?.toDoubleOrNull()?.takeIf { it == floor(it) }?.toLong()
    var amt by remember { mutableStateOf(qrAmount?.toString() ?: "") }
    var confirm by remember { mutableStateOf(false) }
    val value = amt.toLongOrNull()
    val amountOk = value != null && value in 1..MAX_AMOUNT
    val shownId = if (payee.mobile) "+91 ${payee.vpa}" else payee.vpa

    fun launchPay() {
        val v = value ?: return
        if (Prefs.simId(ctx) < 0) {
            Toast.makeText(ctx, tr("Pehle Settings mein SIM chunein", "पहले सेटिंग्स में SIM चुनें"), Toast.LENGTH_LONG).show()
            return
        }
        val now = System.currentTimeMillis()
        TxnStore(ctx).add(Txn(now, PayId.new(), payee.name, payee.vpa, v, now, "PENDING"))
        onSession(UssdJob.Pay(payee.vpa, v.toString(), now, payee.mobile), "${payee.name} • $shownId")
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) launchPay()
        else Toast.makeText(ctx, tr("Call permission chahiye", "कॉल अनुमति चाहिए"), Toast.LENGTH_LONG).show()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackRow(tr("Payment", "पेमेंट"), onBack)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Person, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(tr("Paying to", "भुगतान किसे"), fontSize = 12.sp, color = Color.Gray)
                    Text(payee.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(shownId, fontSize = 14.sp, color = Color.DarkGray)
                }
            }
        }
        OutlinedTextField(
            value = amt, onValueChange = { amt = it.filter(Char::isDigit).take(5) },
            label = { Text(tr("Amount (₹)", "राशि (₹)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            isError = amt.isNotEmpty() && !amountOk,
            supportingText = {
                Text(
                    if (payee.amount != null && qrAmount == null)
                        tr("QR amount decimal hai; *99# par poore rupaye hi chalte hain", "QR राशि दशमलव में है; *99# पर पूरे रुपये ही चलते हैं")
                    else tr("₹1 se ₹$MAX_AMOUNT tak", "₹1 से ₹$MAX_AMOUNT तक")
                )
            }
        )
        InfoNote(
            tr("PIN se pehle bank receiver ka naam dikhayega. Naam sahi na ho to PIN mat daalein.",
                "PIN से पहले बैंक रिसीवर का नाम दिखाएगा। नाम सही न हो तो PIN मत डालें।"),
            Icons.Rounded.Verified
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { confirm = true }, enabled = amountOk,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) { Text(tr("Pay via *99#", "*99# से पे करें"), fontSize = 16.sp) }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(tr("Confirm payment", "पेमेंट कन्फ़र्म करें")) },
            text = { Text("₹$amt → ${payee.name}\n$shownId") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    if (!Ussd.hasCallPermission(ctx)) permLauncher.launch(Manifest.permission.CALL_PHONE)
                    else launchPay()
                }) { Text(tr("Aage badhein", "आगे बढ़ें")) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Cancel", "रद्द")) } }
        )
    }
}

// ---------------------------------------------------------------- HISTORY
@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val v by TxnStore.version.collectAsState()
    val list = remember(v) { TxnStore(ctx).all() }
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow(tr("History", "हिस्ट्री"), onBack)
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr("Abhi koi transaction nahi", "अभी कोई लेनदेन नहीं"), color = Color.Gray)
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { t ->
                    val color = statusColor(t.status)
                    Card(
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onOpen(t.id) },
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(statusIcon(t.status), 40.dp, tint = color, bg = color.copy(alpha = 0.12f))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.receiver.ifBlank { t.payee }, fontWeight = FontWeight.SemiBold)
                                Text(t.vpa, fontSize = 12.sp, color = Color.Gray)
                                Text(fmt.format(Date(t.time)) + "  •  " + t.payId, fontSize = 11.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("₹${t.amount}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(t.status, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Text(
                    tr("Payment history app se delete nahi ho sakti (proof ke liye).",
                        "पेमेंट हिस्ट्री ऐप से डिलीट नहीं हो सकती (प्रूफ के लिए)।"),
                    fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- RECEIPT
@Composable
fun ReceiptScreen(txnId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val t = remember(txnId) { TxnStore(ctx).find(txnId) }
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BackRow(tr("Payment receipt", "पेमेंट रसीद"), onBack)
        if (t == null) {
            Text(tr("Receipt nahi mili", "रसीद नहीं मिली"))
            return@Column
        }
        val color = statusColor(t.status)
        val statusText = when (t.status) {
            "SUCCESS" -> tr("Payment safal", "पेमेंट सफल")
            "FAILED" -> tr("Payment fail", "पेमेंट फ़ेल")
            else -> tr("Result pending", "रिज़ल्ट पेंडिंग")
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(statusIcon(t.status), 64.dp, tint = color, bg = color.copy(alpha = 0.12f))
                Spacer(Modifier.height(10.dp))
                Text("₹${t.amount}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(statusText, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReceiptRow(
                    if (t.receiver.isNotBlank()) tr("Receiver (bank ke anusaar)", "रिसीवर (बैंक के अनुसार)")
                    else tr("Receiver", "रिसीवर"),
                    t.receiver.ifBlank { t.payee }
                )
                ReceiptRow(tr("UPI ID / Mobile", "UPI ID / मोबाइल"), t.vpa)
                ReceiptRow(tr("Payment ID", "पेमेंट ID"), t.payId)
                if (t.bankRef.isNotBlank()) ReceiptRow(tr("Bank reference", "बैंक रेफरेंस"), t.bankRef)
                ReceiptRow(tr("Date & time", "तारीख और समय"), fmt.format(Date(t.time)))
                if (t.detail.isNotBlank()) ReceiptRow(tr("Bank message", "बैंक संदेश"), t.detail)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    clip.setText(AnnotatedString(t.payId))
                    Toast.makeText(ctx, tr("Payment ID copy hui", "पेमेंट ID कॉपी हुई"), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ID")
            }
            Button(
                onClick = {
                    val text = buildString {
                        append("OfflinePay Payment Receipt\n")
                        append("Status: ${t.status}\nAmount: Rs ${t.amount}\n")
                        append("Receiver: ${t.receiver.ifBlank { t.payee }}\n")
                        append("UPI ID/Mobile: ${t.vpa}\nPayment ID: ${t.payId}\n")
                        if (t.bankRef.isNotBlank()) append("Bank Ref: ${t.bankRef}\n")
                        append("Date: ${fmt.format(Date(t.time))}")
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    }
                    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                modifier = Modifier.weight(2f).height(50.dp)
            ) {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Receipt share karein", "रसीद शेयर करें"))
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- SETTINGS
@Composable
fun SettingsScreen(onBack: () -> Unit, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val google = Prefs.userType(ctx) == "google"
    val lockOn = Prefs.lockEnabled(ctx)

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BackRow(tr("Settings", "सेटिंग्स"), onBack)

        SettingRow(
            Icons.Rounded.Person,
            if (google) Prefs.userName(ctx).ifBlank { "Google" } else "Guest",
            if (google) Prefs.userEmail(ctx) else tr("Google se login nahi hai", "Google से लॉगिन नहीं है"),
            onClick = { nav(Screen.Login) },
            trailing = {
                Text(if (google) tr("Badlein", "बदलें") else "Login", color = Purple, fontWeight = FontWeight.SemiBold)
            }
        )
        SettingRow(
            Icons.Rounded.SimCard, tr("Payment SIM", "पेमेंट SIM"),
            Prefs.simLabel(ctx).ifBlank { tr("Chuni nahi gayi", "चुनी नहीं गई") },
            onClick = { nav(Screen.SimPick) },
            trailing = { Text(tr("Badlein", "बदलें"), color = Purple, fontWeight = FontWeight.SemiBold) }
        )
        SettingRow(
            Icons.Rounded.Lock, tr("App Lock", "ऐप लॉक"),
            if (lockOn) tr("PIN se surakshit", "PIN से सुरक्षित") else tr("Band hai", "बंद है"),
            trailing = {
                Switch(checked = lockOn, onCheckedChange = {
                    nav(if (it) Screen.SetLock else Screen.DisableLock)
                })
            }
        )
        SettingRow(
            Icons.Rounded.Language, "हिंदी", tr("Language: Hindi", "भाषा: हिंदी"),
            trailing = {
                Switch(checked = Lang.hindi, onCheckedChange = {
                    Lang.hindi = it
                    ctx.getSharedPreferences("offlinepay", 0).edit().putBoolean("hindi", it).apply()
                })
            }
        )
        InfoNote(
            tr("1. QR/UPI ID/mobile number aur amount daalein.\n2. App *99# SIM se background mein khud chalata hai, koi popup nahi.\n3. PIN se pehle bank receiver ka naam dikhata hai.\n4. PIN isi app mein daalein, wo seedha bank ko jaata hai.\n5. Har payment ka Payment ID aur receipt History mein save hota hai.",
                "1. QR/UPI ID/मोबाइल नंबर और राशि डालें।\n2. ऐप *99# SIM से बैकग्राउंड में खुद चलाता है, कोई पॉपअप नहीं।\n3. PIN से पहले बैंक रिसीवर का नाम दिखाता है।\n4. PIN इसी ऐप में डालें, वह सीधे बैंक को जाता है।\n5. हर पेमेंट का Payment ID और रसीद हिस्ट्री में सेव होती है।")
        )
    }
}
