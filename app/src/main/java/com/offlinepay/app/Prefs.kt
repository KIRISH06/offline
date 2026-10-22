package com.offlinepay.app

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

object Prefs {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("offlinepay", Context.MODE_PRIVATE)

    fun onboarded(c: Context) = sp(c).getBoolean("onboarded", false)
    fun setOnboarded(c: Context, v: Boolean) = sp(c).edit().putBoolean("onboarded", v).apply()

    fun simId(c: Context) = sp(c).getInt("simId", -1)
    fun simLabel(c: Context) = sp(c).getString("simLabel", "") ?: ""
    fun setSim(c: Context, id: Int, label: String) =
        sp(c).edit().putInt("simId", id).putString("simLabel", label).apply()

    fun userType(c: Context) = sp(c).getString("userType", "guest") ?: "guest"
    fun userName(c: Context) = sp(c).getString("userName", "") ?: ""
    fun userEmail(c: Context) = sp(c).getString("userEmail", "") ?: ""
    fun setUser(c: Context, type: String, name: String, email: String) =
        sp(c).edit().putString("userType", type).putString("userName", name)
            .putString("userEmail", email).apply()

    // ---- app lock: PIN is stored only as a salted SHA-256 hash ----
    private fun hash(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun lockEnabled(c: Context) = sp(c).contains("lockHash")

    fun setLockPin(c: Context, pin: String?) {
        val e = sp(c).edit()
        if (pin == null) {
            e.remove("lockHash").remove("lockSalt")
        } else {
            val salt = UUID.randomUUID().toString()
            e.putString("lockSalt", salt).putString("lockHash", hash(salt + pin))
        }
        e.apply()
    }

    fun verifyPin(c: Context, pin: String): Boolean {
        val salt = sp(c).getString("lockSalt", null) ?: return false
        return hash(salt + pin) == sp(c).getString("lockHash", null)
    }
}
