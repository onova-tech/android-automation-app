package com.proj.automation.channel.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.proj.automation.agent.AgentCoordinator
import com.proj.automation.channel.Envelope
import com.proj.automation.channel.PhoneNumbers
import kotlinx.coroutines.launch

/**
 * SMS channel adapter, inbound side. Joins the parts of a multipart message, hands it to the
 * coordinator as a channel-neutral envelope, and sends the replies back to the same number.
 * Senders not on the allowlist are dropped by the router before anything else happens.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val bySender = parts.filterNotNull().groupBy { it.originatingAddress.orEmpty() }
        if (bySender.isEmpty()) return

        val pending = goAsync()
        AgentCoordinator.scope.launch {
            try {
                for ((sender, messages) in bySender) {
                    if (sender.isEmpty()) continue
                    val body = messages.joinToString("") { it.messageBody.orEmpty() }
                    // Use the allow-listed spelling of the number, so formats like +55 11 ... and 11 ... match
                    val channel = AgentCoordinator.smsChannel()
                    val known = PhoneNumbers.matchAllowed(sender, channel.allowedSenders) ?: sender
                    val env = Envelope("sms", known, body, messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis())
                    val replies = AgentCoordinator.handle(env, channel)
                    SmsSender.send(context, sender, replies)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
