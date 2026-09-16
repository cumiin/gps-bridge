package com.gpsbridge.receiver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gpsbridge.common.GpsData
import com.gpsbridge.common.Protocol
import com.gpsbridge.receiver.databinding.ActivityMainBinding
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var testInjector: MockLocationInjector? = null
    private val testHandler = Handler(Looper.getMainLooper())

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startService()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 어떤 빌드가 설치됐는지 화면에서 바로 확인할 수 있게 버전 표시
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        binding.txtTitle.text = "GPS Bridge · 수신 (태블릿)  v$ver"
        binding.txtIp.text = "이 태블릿 IP: ${localIpv4()}\n(폰 송신 앱의 '대상 IP'에 이 값을 입력)"

        binding.btnDev.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        binding.btnTest.setOnClickListener { runMockTest() }

        binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.wifiBox.visibility =
                if (checkedId == R.id.radioBt) View.GONE else View.VISIBLE
        }

        binding.btnStart.setOnClickListener { requestPermsAndStart() }
        binding.btnStop.setOnClickListener { stopService() }

        GpsReceiverService.statusListener = { msg ->
            runOnUiThread { binding.txtStatus.text = msg }
        }
    }

    override fun onDestroy() {
        GpsReceiverService.statusListener = null
        testHandler.removeCallbacksAndMessages(null)
        testInjector?.stop()
        super.onDestroy()
    }

    /** 이 기기의 WiFi IPv4 주소 */
    private fun localIpv4(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
                .firstOrNull { it.isNotBlank() } ?: "확인 불가 (WiFi 연결 확인)"
        } catch (e: Exception) {
            "확인 불가"
        }
    }

    /**
     * 네트워크와 무관하게 '모의 위치 주입' 자체가 동작하는지 확인하는 테스트.
     * 서울시청 좌표를 15초간 계속 주입한다.
     */
    private fun runMockTest() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }
        testHandler.removeCallbacksAndMessages(null)
        testInjector?.stop()

        val inj = MockLocationInjector(this)
        if (!inj.start()) {
            binding.txtStatus.text =
                "❌ 모의 위치 앱으로 지정되지 않았습니다.\n개발자 옵션 → '모의 위치 앱'에서 이 앱을 선택하세요."
            return
        }
        testInjector = inj

        val seoulCityHall = GpsData(
            lat = 37.566535, lon = 126.977969, alt = 38.0,
            accuracy = 3f, speed = 0f, bearing = 0f,
            time = System.currentTimeMillis()
        )

        var ticks = 0
        val tick = object : Runnable {
            override fun run() {
                inj.push(seoulCityHall)
                ticks++
                binding.txtStatus.text =
                    "🧪 테스트 주입 중 ($ticks/15초)  37.566535, 126.977969\n" +
                    "지금 지도 앱에서 서울시청으로 보이면 모의 위치 정상입니다."
                if (ticks < 15) {
                    testHandler.postDelayed(this, 1000)
                } else {
                    inj.stop()
                    testInjector = null
                    binding.txtStatus.text = "🧪 테스트 종료. 지도에 서울시청이 떴다면 모의 위치는 정상입니다."
                }
            }
        }
        testHandler.post(tick)
    }

    private fun requestPermsAndStart() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (binding.radioBt.isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startService() else permLauncher.launch(missing.toTypedArray())
    }

    private fun startService() {
        // 테스트 주입이 돌고 있으면 충돌하지 않게 정리
        testHandler.removeCallbacksAndMessages(null)
        testInjector?.stop()
        testInjector = null

        val intent = Intent(this, GpsReceiverService::class.java)
        if (binding.radioBt.isChecked) {
            intent.putExtra(GpsReceiverService.EXTRA_MODE, GpsReceiverService.MODE_BT)
        } else {
            val port = binding.editPort.text.toString().toIntOrNull()
                ?: Protocol.DEFAULT_UDP_PORT
            intent.putExtra(GpsReceiverService.EXTRA_MODE, GpsReceiverService.MODE_WIFI)
            intent.putExtra(GpsReceiverService.EXTRA_PORT, port)
        }
        ContextCompat.startForegroundService(this, intent)
        binding.btnStart.isEnabled = false
        binding.btnStop.isEnabled = true
    }

    private fun stopService() {
        stopService(Intent(this, GpsReceiverService::class.java))
        binding.btnStart.isEnabled = true
        binding.btnStop.isEnabled = false
        binding.txtStatus.text = "중지됨"
    }
}
