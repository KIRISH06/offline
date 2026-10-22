package com.offlinepay.app

sealed interface Action {
    data class Reply(val text: String) : Action
    data class Wait(val hint: String) : Action
    data class Done(val ok: Boolean, val msg: String) : Action
    data class Abort(val msg: String) : Action
}

/**
 * Decides what to send for the current USSD screen, based on its text.
 * Menus are matched by KEYWORD (not fixed numbers) because banks differ.
 * If a menu is not recognised, the app shows it to the user inside the app.
 */
object UssdFlow {
    private val menuRe = Regex("""^\s*(\d{1,2})\s*[.)\-:]\s*(.+)$""")
    val pinRe = Regex("""\bpin\b""", RegexOption.IGNORE_CASE)

    private val failWords = listOf(
        "fail", "declin", "invalid", "insufficient", "not successful", "unsuccessful",
        "error", "exceed", "wrong", "incorrect", "blocked", "not registered",
        "timed out", "try again", "not allowed"
    )
    private val successWords = listOf("success", "completed", "txn id", "transaction id", "ref no")

    fun options(msg: String): List<Pair<String, String>> =
        msg.lines().mapNotNull { line ->
            menuRe.find(line)?.let { it.groupValues[1] to it.groupValues[2].lowercase().trim() }
        }

    fun decide(job: UssdJob, msg: String): Action {
        val m = msg.lowercase()
        val opts = options(msg)

        if (opts.size >= 2) return pickOption(job, opts)

        if (failWords.any { m.contains(it) }) return Action.Done(false, clean(msg))

        // PIN is typed by the user (in-app masked field or system dialog), never auto-filled.
        if (pinRe.containsMatchIn(msg)) {
            return Action.Wait(tr("UPI PIN daalein", "UPI PIN डालें"))
        }

        return when (job) {
            is UssdJob.Balance ->
                if (m.contains("balance")) Action.Done(true, clean(msg))
                else Action.Wait("")

            is UssdJob.Pay -> when {
                successWords.any { m.contains(it) } -> Action.Done(true, clean(msg))
                m.contains("amount") -> Action.Reply(job.amount)
                job.mobile && m.contains("mobile") -> Action.Reply(job.target)
                !job.mobile && (m.contains("upi id") || m.contains("upi address") ||
                    m.contains("vpa") || m.contains("payee")) -> Action.Reply(job.target)
                m.contains("remark") -> Action.Reply("1")
                else -> Action.Wait("")
            }
        }
    }

    private fun pickOption(job: UssdJob, opts: List<Pair<String, String>>): Action {
        val wanted = when (job) {
            is UssdJob.Balance -> listOf("balance")
            is UssdJob.Pay ->
                if (job.mobile) listOf("mobile no", "mobile number", "send money")
                else listOf("upi id", "upi address", "vpa", "send money")
        }
        for (k in wanted) {
            opts.firstOrNull { it.second.contains(k) }?.let { return Action.Reply(it.first) }
        }
        return Action.Wait("")
    }

    private fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim().take(300)
}
