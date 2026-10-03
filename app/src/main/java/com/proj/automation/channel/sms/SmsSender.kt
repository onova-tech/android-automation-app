package com.proj.automation.channel.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/** SMS channel adapter, outbound side: one SMS per reply page (pages already fit 160 characters). */
object SmsSender {

    fun send(context: Context, to: String, messages: List<String>) {
        if (messages.isEmpty()) return
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        for (text in messages) {
            val parts = manager.divideMessage(text)
            if (parts.size == 1) manager.sendTextMessage(to, null, text, null, null)
            else manager.sendMultipartTextMessage(to, null, parts, null, null)
        }
    }
}
