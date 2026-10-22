package com.offlinepay.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager

/** Sends USSD through the chosen SIM. No system dialog is shown. */
class UssdClient(ctx: Context, subId: Int) {
    private val base = ctx.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val tm: TelephonyManager = if (subId >= 0) base.createForSubscriptionId(subId) else base
    private val handler = Handler(Looper.getMainLooper())

    fun send(text: String, onOk: (String) -> Unit, onFail: (String) -> Unit) {
        try {
            tm.sendUssdRequest(text, object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(t: TelephonyManager, request: String, response: CharSequence) {
                    onOk(response.toString())
                }

                override fun onReceiveUssdResponseFailed(t: TelephonyManager, request: String, failureCode: Int) {
                    onFail(
                        when (failureCode) {
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL ->
                                tr("Network/USSD service abhi available nahi hai", "नेटवर्क/USSD सर्विस अभी उपलब्ध नहीं है")
                            else -> tr("Bank se jawab nahi aaya. Signal check karke dobara try karein.",
                                "बैंक से जवाब नहीं आया। सिग्नल जाँचकर दोबारा कोशिश करें।")
                        }
                    )
                }
            }, handler)
        } catch (e: SecurityException) {
            onFail(tr("Call permission chahiye", "कॉल अनुमति चाहिए"))
        } catch (e: Exception) {
            onFail(e.message ?: "Error")
        }
    }
}
