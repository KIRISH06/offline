package com.offlinepay.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Txn(
    val id: Long,
    val payId: String,
    val payee: String,
    val vpa: String,
    val amount: Long,
    val time: Long,
    val status: String,          // PENDING | SUCCESS | FAILED
    val receiver: String = "",   // name reported by the bank (proof)
    val bankRef: String = "",    // bank/UPI reference if present in the reply
    val detail: String = ""
)

object PayId {
    private val rnd = SecureRandom()
    private const val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun new(): String {
        val ts = SimpleDateFormat("yyMMddHHmmss", Locale.US).format(Date())
        val tail = (1..4).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        return "OP$ts$tail"
    }
}

/** Local history. The app offers no way to delete it. UPI PIN is never stored. */
class TxnStore(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("offlinepay", Context.MODE_PRIVATE)

    companion object {
        val version = MutableStateFlow(0)
        private val lock = Any()
    }

    private fun readAll(): List<Txn> {
        val arr = JSONArray(p.getString("txns", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Txn(
                o.getLong("id"), o.optString("payId"), o.getString("payee"), o.getString("vpa"),
                o.getLong("amount"), o.getLong("time"), o.getString("status"),
                o.optString("receiver"), o.optString("bankRef"), o.optString("detail")
            )
        }
    }

    private fun save(list: List<Txn>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("payId", it.payId).put("payee", it.payee)
                    .put("vpa", it.vpa).put("amount", it.amount).put("time", it.time)
                    .put("status", it.status).put("receiver", it.receiver)
                    .put("bankRef", it.bankRef).put("detail", it.detail)
            )
        }
        p.edit().putString("txns", arr.toString()).apply()
        version.value = version.value + 1
    }

    fun all(): List<Txn> = synchronized(lock) { readAll() }.sortedByDescending { it.time }

    fun find(id: Long): Txn? = synchronized(lock) { readAll() }.firstOrNull { it.id == id }

    fun add(t: Txn) = synchronized(lock) { save(readAll() + t) }

    fun update(id: Long, status: String, detail: String, bankRef: String) = synchronized(lock) {
        save(readAll().map {
            if (it.id == id) it.copy(status = status, detail = detail.take(300), bankRef = bankRef) else it
        })
    }

    fun setReceiver(id: Long, name: String) = synchronized(lock) {
        save(readAll().map { if (it.id == id) it.copy(receiver = name) else it })
    }
}
