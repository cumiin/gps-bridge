package com.gpsbridge.sender

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gpsbridge.common.Protocol
import com.gpsbridge.sender.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var btDevices: List<BluetoothDevice> = emptyList()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
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
        binding.txtTitle.text = "GPS Bridge · 송신 (폰)  v$ver"

        binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val bt = checkedId == R.id.radioBt
            binding.wifiBox.visibility = if (bt) View.GONE else View.VISIBLE
            binding.btBox.visibility = if (bt) View.VISIBLE else View.GONE
            if (bt) loadBondedDevices()
        }

        binding.btnStart.setOnClickListener { requestPermsAndStart() }
        binding.btnStop.setOnClickListener { stopService() }

        GpsSenderService.statusListener = { msg ->
            runOnUiThread { binding.txtStatus.text = msg }
        }
    }

    override fun onDestroy() {
        GpsSenderService.statusListener = null
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
        val intent = Intent(this, GpsSenderService::class.java)
        if (binding.radioBt.isChecked) {
            val pos = binding.spinnerBt.selectedItemPosition
            if (pos < 0 || pos >= btDevices.size) {
                Toast.makeText(this, "블루투스 기기를 선택하세요", Toast.LENGTH_SHORT).show()
                return
            }
            intent.putExtra(GpsSenderService.EXTRA_MODE, GpsSenderService.MODE_BT)
            intent.putExtra(GpsSenderService.EXTRA_BT_MAC, btDevices[pos].address)
        } else {
            val host = binding.editHost.text.toString().ifBlank { Protocol.DEFAULT_BROADCAST }
            val port = binding.editPort.text.toString().toIntOrNull() ?: Protocol.DEFAULT_UDP_PORT
            intent.putExtra(GpsSenderService.EXTRA_MODE, GpsSenderService.MODE_WIFI)
            intent.putExtra(GpsSenderService.EXTRA_HOST, host)
            intent.putExtra(GpsSenderService.EXTRA_PORT, port)
        }
        ContextCompat.startForegroundService(this, intent)
        binding.btnStart.isEnabled = false
        binding.btnStop.isEnabled = true
    }

    private fun stopService() {
        stopService(Intent(this, GpsSenderService::class.java))
        binding.btnStart.isEnabled = true
        binding.btnStop.isEnabled = false
        binding.txtStatus.text = "중지됨"
    }

    @SuppressLint("MissingPermission")
    private fun loadBondedDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            return
        }
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "블루투스를 켜고 태블릿과 페어링하세요", Toast.LENGTH_LONG).show()
            return
        }
        btDevices = adapter.bondedDevices?.toList() ?: emptyList()
        val names = btDevices.map { "${it.name ?: "이름없음"} (${it.address})" }
        binding.spinnerBt.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, names
        )
        if (btDevices.isEmpty()) {
            Toast.makeText(this, "페어링된 기기가 없습니다", Toast.LENGTH_LONG).show()
        }
    }
}
