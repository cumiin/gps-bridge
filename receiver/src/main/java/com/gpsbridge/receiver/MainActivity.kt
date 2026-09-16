package com.gpsbridge.receiver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gpsbridge.common.Protocol
import com.gpsbridge.receiver.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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

        binding.btnDev.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

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
        super.onDestroy()
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
