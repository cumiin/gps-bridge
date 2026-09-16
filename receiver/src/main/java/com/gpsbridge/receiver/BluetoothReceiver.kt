package com.gpsbridge.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import com.gpsbridge.common.GpsData
import com.gpsbridge.common.Protocol
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 태블릿이 SPP 서버로 대기하고, 폰(송신 앱)이 접속하면 한 줄씩 읽는다.
 */
@SuppressLint("MissingPermission")
class BluetoothReceiver(
    private val adapter: BluetoothAdapter,
    private val onData: (GpsData) -> Unit,
    private val onStatus: (String) -> Unit
) {
    @Volatile private var running = false
    private var server: BluetoothServerSocket? = null
    private var client: BluetoothSocket? = null
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread {
            try {
                val s = adapter.listenUsingRfcommWithServiceRecord(
                    Protocol.BT_NAME, Protocol.BT_UUID
                )
                server = s
                onStatus("블루투스 연결 대기 중…")
                val socket = s.accept()
                client = socket
                try { s.close() } catch (_: Exception) {}
                onStatus("연결됨: ${socket.remoteDevice?.name ?: socket.remoteDevice?.address}")

                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                while (running) {
                    val line = reader.readLine() ?: break
                    GpsData.decode(line)?.let(onData)
                }
            } catch (e: Exception) {
                if (running) onStatus(
                    "블루투스 오류: ${e.javaClass.simpleName}: ${e.message ?: "(메시지 없음)"}"
                )
            } finally {
                try { client?.close() } catch (_: Exception) {}
                try { server?.close() } catch (_: Exception) {}
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        try { client?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        thread = null
    }
}
