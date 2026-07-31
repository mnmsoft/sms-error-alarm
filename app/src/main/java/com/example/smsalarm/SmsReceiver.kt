package com.example.smsalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat

/**
 * 문자메시지가 수신될 때마다 호출되는 브로드캐스트 리시버.
 * 메시지 본문에 "ERROR"라는 단어(대소문자 무관)가 포함되어 있으면
 * AlarmService를 실행하여 무음/진동 모드와 상관없이 벨소리를 울린다.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        // 감지할 키워드. 필요하면 여러 개로 바꿔도 됨.
        const val TRIGGER_KEYWORD = "ERROR"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // 하나의 문자가 여러 개의 SMS PDU로 쪼개져 올 수 있으므로 모두 합친다.
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val fullBody = StringBuilder()
        var sender = ""
        for (msg in messages) {
            fullBody.append(msg.messageBody)
            sender = msg.originatingAddress ?: sender
        }
        val body = fullBody.toString()

        if (body.contains(TRIGGER_KEYWORD, ignoreCase = true)) {
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                putExtra(AlarmService.EXTRA_SENDER, sender)
                putExtra(AlarmService.EXTRA_MESSAGE, body)
            }
            // Android 8.0(API 26) 이상에서는 반드시 startForegroundService로 실행해야 함
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
