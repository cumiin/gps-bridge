package com.gpsbridge.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gpsbridge.common.GpsData
import com.gpsbridge.common.Protocol

/**
 * 위치를 수신해 모의 위치로 주입하는 포그라운드 서비스.
 */
class GpsReceiverService : Service() {

    private var injector: MockLocationInjector? = null
    private var udp: UdpReceiver? = null
    private var bt: BluetoothReceiver? = null
    private var count = 0

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("시작 중…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_WIFI

        val inj = MockLocationInjector(this)
        if (!inj.start()) {
            emit("모의 위치 앱으로 지정되지 않았습니다.\n개발자 옵션 → '모의 위치 앱'에서 이 앱을 선택하세요.")
            stopSelf()
            return START_NOT_STICKY
        }
        injector = inj

        try {
            when (mode) {
                MODE_BT -> {
                    val adapter =
                        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                            ?: throw IllegalStateException("블루투스를 지원하지 않습니다")
                    if (!adapter.isEnabled) throw IllegalStateException("블루투스를 켜세요")
                    bt = BluetoothReceiver(
                        adapter,
                        onData = { onData(it) },
                        onStatus = { emit(it) }
                    ).also { it.start() }
                }
                else -> {
                    val port = intent?.getIntExtra(EXTRA_PORT, Protocol.DEFAULT_UDP_PORT)
                        ?: Protocol.DEFAULT_UDP_PORT
                    udp = UdpReceiver(
                        port,
                        onData = { onData(it) },
                        onError = { emit("오류: $it") }
                    ).also { it.start() }
                    emit("WiFi 수신 대기 중 (포트 $port)")
                }
            }
        } catch (e: Exception) {
            emit("오류: ${e.message}")
            stopSelf()
        }
        return START_STICKY
    }

    private fun onData(d: GpsData) {
        injector?.push(d)
        count++
        emit("수신 중 ($count)  %.6f, %.6f".format(d.lat, d.lon))
    }

    override fun onDestroy() {
        udp?.stop()
        bt?.stop()
        injector?.stop()
        emit("중지됨")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun emit(msg: String) {
        statusListener?.invoke(msg)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(msg))
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "gps_receiver"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId, "GPS 수신", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS Bridge 수신")
            .setContentText(text.take(60))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIF_ID = 2001
        const val MODE_WIFI = "wifi"
        const val MODE_BT = "bt"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PORT = "port"

        var statusListener: ((String) -> Unit)? = null
    }
}
