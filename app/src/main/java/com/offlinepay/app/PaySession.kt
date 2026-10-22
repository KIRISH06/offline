package com.offlinepay.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface SState {
    data class Working(val stage: Int) : SState
    data class NeedPin(val bankMsg: String, val receiver: String) : SState
    data class Manual(val msg: String) : SState
    data class Done(val ok: Boolean, val msg: String) : SState
}

/**
 * Runs the whole *99# conversation inside the app (no system popup).
 * The UPI PIN goes straight to the SIM request and is never stored or logged.
 */
class PaySession(ctx: Context, private val job: UssdJob) {
    private val app = ctx.applicationContext
    private val client = UssdClient(app, Prefs.simId(app))
    private val store = TxnStore(app)

    var state by mutableStateOf<SState>(SState.Working(0))
        private set

    private var steps = 0
    private var started = false
    private var pinSent = false
    private var finished = false

    /** False while the PIN has been sent and the result is not yet known. */
    val canLeave: Boolean get() = finished || !pinSent

    companion object {
        private val receiverPatterns = listOf(
            Regex("(?i)(?:payee|beneficiary|receiver)(?:\\s*name)?\\s*[:\\-]\\s*([A-Za-z][A-Za-z .']{2,40})"),
            Regex("(?i)\\bname\\s*[:\\-]\\s*([A-Za-z][A-Za-z .']{2,40})"),
            Regex("(?i)\\bpay(?:ing)?\\s+(?:rs\\.?|inr)?\\s*[\\d.]+\\s+to\\s+([A-Za-z][A-Za-z .']{2,40})")
        )
        private val cutRe =
            Regex("(?i)(\\s+(enter|please|press|reply|upi|pin|rs|inr|amount|amt|vpa|ref|txn|mobile|for)\\b|[,;(]).*$")

        /** Best effort: the name the bank shows before the PIN prompt. */
        fun extractReceiver(msg: String): String {
            val flat = msg.replace(Regex("\\s+"), " ")
            for (p in receiverPatterns) {
                val m = p.find(flat) ?: continue
                val n = m.groupValues[1].replace(cutRe, "").trim(' ', '.', '-', ':')
                if (n.length >= 3) return n
            }
            return ""
        }

        fun extractRef(msg: String): String =
            Regex("(?i)(?:txn|transaction|ref(?:erence)?|rrn|utr)\\s*(?:id|no\\.?|number)?\\s*[:\\-]?\\s*(?=[A-Za-z0-9]*\\d)([A-Za-z0-9]{6,})")
                .find(msg)?.groupValues?.get(1) ?: ""
    }

    fun start() {
        if (started) return
        started = true
        send("*99#")
    }

    fun reply(text: String) = send(text)

    fun submitPin(pin: String) {
        pinSent = true
        send(pin)
    }

    fun cancel() {
        if (finished) return
        finished = true
        if (job is UssdJob.Pay && !pinSent) store.update(job.txnId, "FAILED", "Cancelled", "")
    }

    private fun send(text: String) {
        val stage = when {
            pinSent -> 2
            steps >= 2 -> 1
            else -> 0
        }
        state = SState.Working(stage)
        client.send(text, { onResponse(it) }, { onFailure(it) })
    }

    private fun onResponse(msg: String) {
        if (finished) return
        if (++steps > 15) {
            finish(false, tr("Menu samajh nahi aaya. Roka gaya.", "मेन्यू समझ नहीं आया। रोका गया।"))
            return
        }
        when (val a = UssdFlow.decide(job, msg)) {
            is Action.Reply -> send(a.text)
            is Action.Wait ->
                if (UssdFlow.pinRe.containsMatchIn(msg)) {
                    val name = extractReceiver(msg)
                    if (name.isNotBlank() && job is UssdJob.Pay) store.setReceiver(job.txnId, name)
                    state = SState.NeedPin(msg.trim(), name)
                } else {
                    state = SState.Manual(msg.trim())
                }
            is Action.Done -> finish(a.ok, a.msg)
            is Action.Abort -> finish(false, a.msg)
        }
    }

    private fun onFailure(reason: String) {
        if (finished) return
        if (pinSent && job is UssdJob.Pay) {
            // Outcome unknown: keep history as PENDING and ask the user to verify.
            finished = true
            state = SState.Done(
                false,
                tr("Result pata nahi chala. Dobara pay karne se pehle bank SMS ya balance check karein.",
                    "नतीजा पता नहीं चला। दोबारा पे करने से पहले बैंक SMS या बैलेंस जाँचें।")
            )
        } else {
            finish(false, reason)
        }
    }

    private fun finish(ok: Boolean, msg: String) {
        finished = true
        var shown = msg
        if (job is UssdJob.Pay) {
            if (!ok && !pinSent) {
                shown = msg + "\n" + tr("PIN nahi daala gaya, aapke paise nahi kate.", "PIN नहीं डाला गया, आपके पैसे नहीं कटे।")
            }
            store.update(job.txnId, if (ok) "SUCCESS" else "FAILED", shown, extractRef(msg))
            if (ok) {
                val t = store.find(job.txnId)
                val who = t?.receiver?.ifBlank { null } ?: t?.payee ?: ""
                Notifier.success(app, job.amount, who, t?.payId ?: "")
            }
        }
        state = SState.Done(ok, shown)
    }
}
