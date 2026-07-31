package com.example.smsalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 알림의 "알람 끄기" 버튼을 눌렀을 때 AlarmService를 종료시키기 위한 리시버.
 */
class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.stopService(Intent(context, AlarmService::class.java))
    }
}
