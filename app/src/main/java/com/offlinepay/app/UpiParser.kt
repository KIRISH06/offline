package com.offlinepay.app

import android.net.Uri

data class UpiPayee(
    val vpa: String,
    val name: String,
    val amount: String? = null,
    val note: String? = null,
    val mobile: Boolean = false
)

object UpiParser {
    private val vpaRe = Regex("^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$")

    fun isValidVpa(v: String) = vpaRe.matches(v.trim())

    /** upi://pay?pa=name@bank&pn=Name&am=100&tn=note */
    fun parse(raw: String): UpiPayee? {
        val text = raw.trim()
        if (!text.startsWith("upi://pay", ignoreCase = true)) return null
        val uri = Uri.parse(text)
        val pa = uri.getQueryParameter("pa")?.trim().orEmpty()
        if (!isValidVpa(pa)) return null
        val pn = uri.getQueryParameter("pn")?.trim().orEmpty().ifBlank { pa }
        val am = uri.getQueryParameter("am")?.trim()
        return UpiPayee(pa, pn, am, uri.getQueryParameter("tn"))
    }
}
