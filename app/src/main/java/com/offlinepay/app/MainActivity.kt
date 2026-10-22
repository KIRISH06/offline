package com.offlinepay.app

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

sealed interface Screen {
    data object Home : Screen
    data object Scan : Screen
    data object Manual : Screen
    data object Mobile : Screen
    data object History : Screen
    data object Settings : Screen
    data object Login : Screen
    data object SimPick : Screen
    data object SetLock : Screen
    data object DisableLock : Screen
    data class Pay(val payee: UpiPayee) : Screen
    data class Session(val job: UssdJob, val subtitle: String) : Screen
    data class Receipt(val txnId: Long) : Screen
}

class MainActivity : ComponentActivity() {
    private var leftAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Payment app: block screenshots, screen recording and the recent-apps preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        Lang.hindi = getSharedPreferences("offlinepay", MODE_PRIVATE).getBoolean("hindi", false)
        LockState.locked = Prefs.lockEnabled(this)
        setContent { OfflinePayTheme { App() } }
    }

    override fun onStop() {
        super.onStop()
        leftAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock if the app was away for more than 10 seconds.
        if (leftAt != 0L && Prefs.lockEnabled(this) &&
            System.currentTimeMillis() - leftAt > 10_000
        ) LockState.locked = true
    }
}

private class SetupState(val startStep: Int, val repair: Boolean)

private fun computeSetup(ctx: android.content.Context): SetupState? = when {
    !Prefs.onboarded(ctx) -> SetupState(0, false)
    !Perms.allGranted(ctx) -> SetupState(2, true)
    !simValid(ctx) -> SetupState(3, true)
    else -> null
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var setup by remember { mutableStateOf(computeSetup(ctx)) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val home = { screen = Screen.Home }

    BackHandler(enabled = setup == null && screen != Screen.Home && screen !is Screen.Session) { home() }

    val callPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) screen = Screen.Session(UssdJob.Balance, "")
        else Toast.makeText(ctx, tr("Call permission chahiye", "कॉल अनुमति चाहिए"), Toast.LENGTH_LONG).show()
    }
    val onBalance = {
        when {
            Prefs.simId(ctx) < 0 ->
                Toast.makeText(ctx, tr("Pehle Settings mein SIM chunein", "पहले सेटिंग्स में SIM चुनें"), Toast.LENGTH_LONG).show()
            !Ussd.hasCallPermission(ctx) -> callPerm.launch(Manifest.permission.CALL_PHONE)
            else -> screen = Screen.Session(UssdJob.Balance, "")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val s = setup
            if (s != null) {
                Onboarding(s.startStep, s.repair, onFinish = { setup = null; screen = Screen.Home })
            } else when (val sc = screen) {
                Screen.Home -> HomeScreen(go = { screen = it }, onBalance = { onBalance() })
                Screen.Scan -> ScanScreen(onPayee = { screen = Screen.Pay(it) }, onBack = home)
                Screen.Manual -> ManualScreen(onNext = { screen = Screen.Pay(it) }, onBack = home)
                Screen.Mobile -> MobileScreen(onNext = { screen = Screen.Pay(it) }, onBack = home)
                Screen.History -> HistoryScreen(onOpen = { screen = Screen.Receipt(it) }, onBack = home)
                Screen.Settings -> SettingsScreen(onBack = home, nav = { screen = it })
                Screen.Login -> LoginScreen(onDone = { screen = Screen.Settings }, onBack = { screen = Screen.Settings })
                Screen.SimPick -> SimPickScreen(onDone = { screen = Screen.Settings }, onBack = { screen = Screen.Settings })
                Screen.SetLock -> PinSetupScreen(onSaved = { screen = Screen.Settings }, onBack = { screen = Screen.Settings })
                Screen.DisableLock -> PinDisableScreen(onDone = { screen = Screen.Settings }, onBack = { screen = Screen.Settings })
                is Screen.Pay -> PayScreen(sc.payee, onBack = home, onSession = { job, sub -> screen = Screen.Session(job, sub) })
                is Screen.Session -> SessionScreen(
                    sc.job, sc.subtitle, onClose = home, onReceipt = { screen = Screen.Receipt(it) }
                )
                is Screen.Receipt -> ReceiptScreen(sc.txnId, onBack = home)
            }
        }
        if (LockState.locked) LockOverlay()
    }
}
