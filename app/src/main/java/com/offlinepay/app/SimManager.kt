package com.offlinepay.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimItem(val subId: Int, val slot: Int, val carrier: String) {
    val label get() = "SIM $slot • $carrier"
}

object SimManager {
    @SuppressLint("MissingPermission")
    fun active(ctx: Context): List<SimItem> {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        val sm = ctx.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val list = try { sm.activeSubscriptionInfoList } catch (e: SecurityException) { null }
        return (list ?: emptyList()).map {
            SimItem(it.subscriptionId, it.simSlotIndex + 1, it.carrierName?.toString() ?: "SIM")
        }.sortedBy { it.slot }
    }
}
