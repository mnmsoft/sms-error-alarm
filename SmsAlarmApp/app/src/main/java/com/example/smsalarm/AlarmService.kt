package com.example.smsalarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * 실제로 "무음/진동 모드와 상관없이" 소리를 내는 핵심 컴포넌트.
 *
 * 안드로이드의 벨소리(RING) 스트림은 무음/진동 모드의 영향을 받지만,
 * 알람(ALARM) 스트림은 그 영향을 받지 않는다. 그래서 STREAM_ALARM /
 * USAGE_ALARM 속성으로 소리를 재생하면 사용자가 폰을 무음이나 진동으로
 * 해놔도 소리가 울린다. (단, 사용자가 물리적으로 미디어/알람 볼륨을
 * 0으로 낮춰놓은 경우는 예외이므로, 아래 코드에서 알람 볼륨을 최대로 강제 설정한다.)
 */
class AlarmService : Service() {

    companion object {
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_MESSAGE = "extra_message"
        const val CHANNEL_ID = "sms_error_alarm_channel"
        const val NOTIFICATION_ID = 1001
        const val AUTO_STOP_MILLIS = 30_000L // 30초 후 자동 정지 (원하는 값으로 조정 가능)
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var originalAlarmVolume: Int = -1
    private var countDownTimer: CountDownTimer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra(EXTRA_SENDER) ?: "알 수 없음"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: ""

        startForeground(NOTIFICATION_ID, buildNotification(sender, message))
        startAlarm()

        // 지정된 시간 뒤 자동으로 알람을 멈춘다 (배터리/방해 방지 목적)
        countDownTimer = object : CountDownTimer(AUTO_STOP_MILLIS, AUTO_STOP_MILLIS) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() { stopAlarmAndSelf() }
        }.start()

        return START_NOT_STICKY
    }

    private fun startAlarm() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 1) 알람(STREAM_ALARM) 볼륨을 최대로 강제 설정 -> 무음/진동 모드 무시
        originalAlarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
        val maxAlarmVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 0
        audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0)

        // 2) 알람용 오디오 포커스 요청
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        audioManager?.requestAudioFocus(
            null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )

        // 3) 기기의 기본 알람음을 STREAM_ALARM으로 반복 재생
        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(this@AlarmService, alarmUri)
            isLooping = true
            setOnPreparedListener { start() }
            prepareAsync()
        }

        // 4) 무음/진동 모드와 무관하게 진동도 함께 울려 확실히 알아채도록 함
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun buildNotification(sender: String, message: String): Notification {
        val stopIntent = Intent(this, StopAlarmReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ ERROR 문자 감지됨")
            .setContentText("발신: $sender / 내용: $message")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "알람 끄기", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS ERROR 알람",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ERROR 문자 수신 시 울리는 알람 채널"
                setBypassDnd(true) // 방해금지 모드에서도 알림을 통과시킴
                enableVibration(true)
                // 이 채널 자체는 소리를 재생하지 않음 (MediaPlayer로 별도 재생하기 때문)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun stopAlarmAndSelf() {
        countDownTimer?.cancel()
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        vibrator?.cancel()

        // 원래 알람 볼륨으로 복원
        if (originalAlarmVolume >= 0) {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAlarmAndSelf()
        super.onDestroy()
    }
}
