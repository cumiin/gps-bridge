package com.gpsbridge.sender

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gpsbridge.common.GpsData
import com.gpsbridge.common.Protocol

/**
 * 폰 GPS 를 읽어 WiFi(UDP) 또는 블루투스로 계속 전송하는 포그라운드 서비스.
 *
 * 네트워크 I/O 는 반드시 메인 스레드 밖에서 해야 하므로(NetworkOnMainThreadException),
 * 전용 HandlerThread 를 만들어 소켓 열기·전송·위치 콜백을 모두 그 스레드에서 처리한다.
 */
class GpsSenderService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private var ioThread: HandlerThread? = null
    private var ioHandler: Handler? = null

    private var udp: UdpSender? = null
    private var bt: BluetoothSender? = null
    private var mode: String = MODE_WIFI
    private var count = 0

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val t = HandlerThread("gps-sender-io").also { it.start() }
        ioThread = t
        ioHandler = Handler(t.looper)
        startForeground(NOTIF_ID, buildNotification("시작 중…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_WIFI
        val host = intent?.getStringExtra(EXTRA_HOST) ?: Protocol.DEFAULT_BROADCAST
        val rawPort = intent?.getIntExtra(EXTRA_PORT, Protocol.DEFAULT_UDP_PORT)
            ?: Protocol.DEFAULT_UDP_PORT
        val port = if (rawPort in 1..65535) rawPort else Protocol.DEFAULT_UDP_PORT
        val mac = intent?.getStringExtra(EXTRA_BT_MAC)

        // 소켓 열기와 위치 구독을 모두 IO 스레드에서 수행
        ioHandler?.post {
            closeTransports()
            try {
                when (mode) {
                    MODE_BT -> {
                        if (mac == null) throw IllegalStateException("블루투스 기기를 선택하세요")
                        val adapter =
                            (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                                ?: throw IllegalStateException("블루투스를 지원하지 않습니다")
                        val device = adapter.getRemoteDevice(mac)
                        bt = BluetoothSender(device).also { it.open() }
                        notifyStatus("블루투스 연결됨: $mac")
                    }
                    else -> {
                        udp = UdpSender(host, port).also { it.open() }
                        notifyStatus("WiFi 전송 준비: $host:$port")
                    }
                }
                startLocationUpdates()
            } catch (e: Exception) {
                emit("오류: ${e.javaClass.simpleName}: ${e.message ?: "(메시지 없음)"}")
                stopSelf()
            }
        }
        return START_STICKY
    }

    /** IO 스레드에서 호출. 위치 콜백도 같은 스레드로 받아 전송이 메인 스레드를 타지 않게 한다. */
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            emit("위치 권한이 없습니다")
            stopSelf()
            return
        }
        val looper = ioThread?.looper ?: return
        val providers = mutableListOf<String>()
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) {
            emit("GPS 가 꺼져 있습니다. 위치를 켜세요.")
            stopSelf()
            return
        }
        for (p in providers) {
            locationManager.requestLocationUpdates(p, 500L, 0f, this, looper)
        }
    }

    /** IO 스레드에서 호출된다. */
    override fun onLocationChanged(location: Location) {
        val data = GpsData(
            lat = location.latitude,
            lon = location.longitude,
            alt = if (location.hasAltitude()) location.altitude else 0.0,
            accuracy = if (location.hasAccuracy()) location.accuracy else 5f,
            speed = if (location.hasSpeed()) location.speed else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            time = System.currentTimeMillis()
        )
        try {
            when (mode) {
                MODE_BT -> bt?.send(data.encode())
                else -> udp?.send(data.encode())
            }
            count++
            emit("전송 중 ($count)  %.6f, %.6f".format(location.latitude, location.longitude))
        } catch (e: Exception) {
            emit("전송 오류: ${e.javaClass.simpleName}: ${e.message ?: "(메시지 없음)"}")
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    private fun closeTransports() {
        udp?.close()
        bt?.close()
        udp = null
        bt = null
    }

    override fun onDestroy() {
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        ioHandler?.post { closeTransports() }
        ioThread?.quitSafely()
        ioThread = null
        ioHandler = null
        emit("중지됨")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun emit(msg: String) {
        statusListener?.invoke(msg)
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "gps_sender"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId, "GPS 송신", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS Bridge 송신")
            .setContentText(text.take(60))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun notifyStatus(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
        emit(text)
    }

    companion object {
        const val NOTIF_ID = 1001
        const val MODE_WIFI = "wifi"
        const val MODE_BT = "bt"
        const val EXTRA_MODE = "mode"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_BT_MAC = "bt_mac"

        /** MainActivity 가 상태 표시를 위해 구독 */
        var statusListener: ((String) -> Unit)? = null
    }
}
