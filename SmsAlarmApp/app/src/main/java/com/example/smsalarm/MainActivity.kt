package com.example.smsalarm

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.smsalarm.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatusText()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermissions.setOnClickListener { requestNeededPermissions() }
        binding.btnDndAccess.setOnClickListener { requestDndAccess() }
        binding.btnBatteryOptimization.setOnClickListener { requestIgnoreBatteryOptimizations() }
        binding.btnTest.setOnClickListener { triggerTestAlarm() }

        requestNeededPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
    }

    /** 문자 수신 감지 + 알림 표시에 필요한 런타임 권한을 요청 */
    private fun requestNeededPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            requestPermissionsLauncher.launch(notGranted.toTypedArray())
        }
    }

    /** 방해금지 모드(DND)에서도 확실히 울리도록 정책 접근 권한 요청 */
    private fun requestDndAccess() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    /** 백그라운드에서 앱이 시스템에 의해 종료되지 않도록 배터리 최적화 제외 요청 */
    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    /** 실제 문자 없이도 알람 동작을 바로 테스트해볼 수 있는 버튼 */
    private fun triggerTestAlarm() {
        val serviceIntent = Intent(this, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_SENDER, "테스트")
            putExtra(AlarmService.EXTRA_MESSAGE, "이것은 테스트 ERROR 메시지입니다")
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun updateStatusText() {
        val smsGranted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECEIVE_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val nm = getSystemService(NotificationManager::class.java)
        val dndGranted = nm.isNotificationPolicyAccessGranted
        val pm = getSystemService(PowerManager::class.java)
        val batteryExempt = pm.isIgnoringBatteryOptimizations(packageName)

        binding.tvStatus.text = buildString {
            append("문자 수신 권한: ${if (smsGranted) "✅ 허용됨" else "❌ 필요"}\n")
            append("방해금지 모드 접근: ${if (dndGranted) "✅ 허용됨" else "❌ 필요"}\n")
            append("배터리 최적화 제외: ${if (batteryExempt) "✅ 완료" else "❌ 권장"}")
        }
    }
}
