package com.offlinepay.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Context

/** One permission group, with a plain-language guide. */
class PermStep(
    val icon: ImageVector,
    val titleEn: String, val titleHi: String,
    val whyEn: String, val whyHi: String,
    val guideEn: List<String>, val guideHi: List<String>,
    val perms: List<String>
) {
    fun granted(ctx: Context) = perms.all {
        ContextCompat.checkSelfPermission(ctx, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

object Perms {
    val steps: List<PermStep> = buildList {
        add(
            PermStep(
                Icons.Rounded.Call,
                "Phone permission", "फ़ोन अनुमति",
                "*99# ko SIM se chalane aur aapki SIM pehchanne ke liye.",
                "*99# को SIM से चलाने और आपकी SIM पहचानने के लिए।",
                listOf(
                    "Neeche \"Permission dein\" dabayein.",
                    "Popup mein \"Allow\" ya \"Allow\" dabayein (Phone calls).",
                    "Dobara popup aaye (phone state) to bhi \"Allow\" dabayein."
                ),
                listOf(
                    "नीचे \"Permission दें\" दबाएं।",
                    "पॉपअप में \"Allow\" दबाएं (Phone calls)।",
                    "दोबारा पॉपअप आए (phone state) तो भी \"Allow\" दबाएं।"
                ),
                listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
            )
        )
        add(
            PermStep(
                Icons.Rounded.CameraAlt,
                "Camera permission", "कैमरा अनुमति",
                "Payment QR code scan karne ke liye. Photo save nahi hoti.",
                "पेमेंट QR कोड स्कैन करने के लिए। फ़ोटो सेव नहीं होती।",
                listOf(
                    "Neeche \"Permission dein\" dabayein.",
                    "Popup mein \"While using the app\" ya \"Allow\" chunein."
                ),
                listOf(
                    "नीचे \"Permission दें\" दबाएं।",
                    "पॉपअप में \"While using the app\" या \"Allow\" चुनें।"
                ),
                listOf(Manifest.permission.CAMERA)
            )
        )
        if (Build.VERSION.SDK_INT >= 33) {
            add(
                PermStep(
                    Icons.Rounded.Notifications,
                    "Notification permission", "नोटिफ़िकेशन अनुमति",
                    "Payment safal hone par notification aur Payment ID dikhane ke liye.",
                    "पेमेंट सफल होने पर नोटिफ़िकेशन और Payment ID दिखाने के लिए।",
                    listOf(
                        "Neeche \"Permission dein\" dabayein.",
                        "Popup mein \"Allow\" dabayein."
                    ),
                    listOf(
                        "नीचे \"Permission दें\" दबाएं।",
                        "पॉपअप में \"Allow\" दबाएं।"
                    ),
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }
    }

    fun missing(ctx: Context) = steps.filter { !it.granted(ctx) }
    fun allGranted(ctx: Context) = missing(ctx).isEmpty()
}

fun simValid(ctx: Context): Boolean {
    val id = Prefs.simId(ctx)
    return id >= 0 && SimManager.active(ctx).any { it.subId == id }
}

@Composable
fun StepFrame(
    step: Int, total: Int, title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(total) { i ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i <= step) Purple else Color(0xFFD6D0DE))
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tr("Step ${step + 1} / $total", "स्टेप ${step + 1} / $total"),
                fontSize = 12.sp, color = Color.Gray
            )
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun Onboarding(startStep: Int, repair: Boolean, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(startStep) }
    val total = 5

    fun finish() {
        Prefs.setOnboarded(ctx, true)
        onFinish()
    }

    fun afterPerms() {
        if (repair && simValid(ctx)) finish() else step = 3
    }

    when (step) {
        0 -> StepFrame(0, total, tr("OfflinePay mein swagat hai", "OfflinePay में स्वागत है")) {
            IconBadge(Icons.Rounded.Security, 72.dp)
            Text(
                tr("Bina internet UPI payment, *99# se. Sab kuch is app ke andar hota hai, koi popup nahi.",
                    "बिना इंटरनेट UPI पेमेंट, *99# से। सब कुछ इसी ऐप के अंदर होता है, कोई पॉपअप नहीं।"),
                fontSize = 15.sp, color = Color.DarkGray
            )
            InfoNote(
                tr("Shuru karne ke liye 5 chhote steps: login, permissions, SIM chunna aur lock. Lagbhag 1 minute lagega.",
                    "शुरू करने के लिए 5 छोटे स्टेप: लॉगिन, परमिशन, SIM चुनना और लॉक। लगभग 1 मिनट लगेगा।")
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Language, null, tint = Purple)
                Spacer(Modifier.width(12.dp))
                Text("हिंदी", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(checked = Lang.hindi, onCheckedChange = {
                    Lang.hindi = it
                    ctx.getSharedPreferences("offlinepay", 0).edit().putBoolean("hindi", it).apply()
                })
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(tr("Shuru karein", "शुरू करें"), fontSize = 16.sp)
            }
        }

        1 -> StepFrame(1, total, tr("Login", "लॉगिन")) {
            Text(
                tr("Google se login karein ya Guest ke roop mein chalayein. Payment ke liye bank PIN hi lagta hai.",
                    "Google से लॉगिन करें या Guest के रूप में चलाएं। पेमेंट के लिए बैंक PIN ही लगता है।"),
                fontSize = 15.sp, color = Color.DarkGray
            )
            LoginPanel(onDone = { step = 2 })
        }

        2 -> PermissionStep(total, onAllGranted = { afterPerms() })

        3 -> StepFrame(3, total, tr("Payment SIM chunein", "पेमेंट SIM चुनें")) {
            SimPickerPanel(
                confirmLabel = tr("Aage badhein", "आगे बढ़ें"),
                onConfirm = { if (repair) finish() else step = 4 }
            )
        }

        else -> {
          var setting by remember { mutableStateOf(false) }
          if (setting) {
            PinSetupScreen(onSaved = { finish() }, onBack = { setting = false })
          } else StepFrame(4, total, tr("App Lock", "ऐप लॉक")) {
            IconBadge(Icons.Rounded.Lock, 72.dp)
            Text(
                tr("App Lock lagane se koi aur aapka app khol kar payment history ya balance nahi dekh payega. Chaahein to abhi skip karke baad mein Settings se laga sakte hain.",
                    "ऐप लॉक लगाने से कोई और आपका ऐप खोलकर पेमेंट हिस्ट्री या बैलेंस नहीं देख पाएगा। चाहें तो अभी स्किप करके बाद में सेटिंग्स से लगा सकते हैं।"),
                fontSize = 15.sp, color = Color.DarkGray
            )
            Button(onClick = { setting = true }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Rounded.Lock, null)
                Spacer(Modifier.width(8.dp))
                Text(tr("App Lock lagayein", "ऐप लॉक लगाएं"))
            }
            OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(tr("Abhi skip karein", "अभी स्किप करें"))
            }
          }
        }
    }
}

@Composable
private fun PermissionStep(total: Int, onAllGranted: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val missing = remember(tick) { Perms.missing(ctx) }
    val current = missing.firstOrNull()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        denied = !(current?.granted(ctx) ?: true)
        tick++
    }

    LaunchedEffect(current) { if (current == null) onAllGranted() }

    StepFrame(2, total, tr("Zaroori permissions", "ज़रूरी परमिशन")) {
        val doneCount = Perms.steps.size - missing.size
        Text(
            tr("Sab permissions dena zaroori hai. $doneCount / ${Perms.steps.size} ho chuki.",
                "सभी परमिशन देना ज़रूरी है। $doneCount / ${Perms.steps.size} हो चुकी।"),
            fontSize = 14.sp, color = Color.DarkGray
        )
        // overview of all permissions
        Perms.steps.forEach { s ->
            val ok = s.granted(ctx)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                    tint = if (ok) Green else Color.Gray
                )
                Spacer(Modifier.width(10.dp))
                Text(tr(s.titleEn, s.titleHi), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (current != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Soft)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(current.icon, 44.dp, bg = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text(tr(current.titleEn, current.titleHi), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(tr(current.whyEn, current.whyHi), fontSize = 13.sp, color = Color.DarkGray)
                    Text(tr("Kaise dein:", "कैसे दें:"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    val guide = if (Lang.hindi) current.guideHi else current.guideEn
                    guide.forEachIndexed { i, g ->
                        Text("${i + 1}. $g", fontSize = 13.sp)
                    }
                    if (denied) {
                        Text(
                            tr("Permission nahi mili. Dobara try karein. Popup na aaye to App Settings kholkar Permissions mein \"Allow\" karein.",
                                "परमिशन नहीं मिली। दोबारा कोशिश करें। पॉपअप न आए तो App Settings खोलकर Permissions में \"Allow\" करें।"),
                            fontSize = 13.sp, color = Red
                        )
                    }
                }
            }
            Button(
                onClick = { launcher.launch(current.perms.toTypedArray()) },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(tr("Permission dein", "Permission दें"), fontSize = 16.sp) }
            if (denied) {
                OutlinedButton(
                    onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text(tr("App Settings kholein", "App Settings खोलें")) }
            }
            Text(
                tr("Jab tak sab permissions nahi milti, aage nahi badh sakte.",
                    "जब तक सब परमिशन नहीं मिलतीं, आगे नहीं बढ़ सकते।"),
                fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
