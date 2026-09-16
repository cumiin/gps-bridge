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
    private lateinit var prefs: Prefs
    private var btDevices: List<BluetoothDevice> = emptyList()

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
        prefs = Prefs(this)

        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        binding.txtTitle.text = "GPS Bridge · 송신 (폰)  v$ver"

        restoreSettings()

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

        // 자동 시작이 켜져 있고 권한이 이미 있으면 바로 시작
        if (prefs.autoStart && hasRequiredPerms()) {
            startService()
        }
    }

    override fun onDestroy() {
        GpsSenderService.statusListener = null
        super.onDestroy()
    }

    private fun restoreSettings() {
        binding.editHost.setText(prefs.host)
        binding.editPort.setText(prefs.port.toString())
        binding.chkAutoStart.isChecked = prefs.autoStart
        if (prefs.mode == GpsSenderService.MODE_BT) {
            binding.radioBt.isChecked = true
            binding.wifiBox.visibility = View.GONE
            binding.btBox.visibility = View.VISIBLE
            loadBondedDevices()
        }
    }

    private fun saveSettings() {
        prefs.autoStart = binding.chkAutoStart.isChecked
        prefs.mode = if (binding.radioBt.isChecked) {
            GpsSenderService.MODE_BT
        } else {
            GpsSenderService.MODE_WIFI
        }
        prefs.host = binding.editHost.text.toString().ifBlank { Protocol.DEFAULT_BROADCAST }
        prefs.port = binding.editPort.text.toString().toIntOrNull() ?: Protocol.DEFAULT_UDP_PORT
    }

    private fun hasRequiredPerms(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

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
        saveSettings()

        val intent = Intent(this, GpsSenderService::class.java)
        if (binding.radioBt.isChecked) {
            val pos = binding.spinnerBt.selectedItemPosition
            val mac = if (pos >= 0 && pos < btDevices.size) {
                btDevices[pos].address
            } else {
                prefs.btMac.ifBlank { null }
            }
            if (mac == null) {
                Toast.makeText(this, "블루투스 기기를 선택하세요", Toast.LENGTH_SHORT).show()
                return
            }
            prefs.btMac = mac
            intent.putExtra(GpsSenderService.EXTRA_MODE, GpsSenderService.MODE_BT)
            intent.putExtra(GpsSenderService.EXTRA_BT_MAC, mac)
        } else {
            intent.putExtra(GpsSenderService.EXTRA_MODE, GpsSenderService.MODE_WIFI)
            intent.putExtra(GpsSenderService.EXTRA_HOST, prefs.host)
            intent.putExtra(GpsSenderService.EXTRA_PORT, prefs.port)
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
        // 저장된 기기를 자동 선택
        val savedIdx = btDevices.indexOfFirst { it.address == prefs.btMac }
        if (savedIdx >= 0) binding.spinnerBt.setSelection(savedIdx)

        if (btDevices.isEmpty()) {
            Toast.makeText(this, "페어링된 기기가 없습니다", Toast.LENGTH_LONG).show()
        }
    }
}
