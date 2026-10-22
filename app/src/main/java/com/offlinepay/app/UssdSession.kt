package com.offlinepay.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

sealed interface UssdJob {
    data class Pay(val target: String, val amount: String, val txnId: Long, val mobile: Boolean = false) : UssdJob
    data object Balance : UssdJob
}

object Ussd {
    fun hasCallPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
}
