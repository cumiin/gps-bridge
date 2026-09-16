package com.gpsbridge.receiver

import com.gpsbridge.common.GpsData
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

/**
 * 지정 포트에서 UDP 위치 패킷을 계속 수신한다.
 */
class UdpReceiver(
    private val port: Int,
    private val onData: (GpsData) -> Unit,
    private val onError: (String) -> Unit
) {
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread {
            try {
                val s = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1000
                    bind(java.net.InetSocketAddress(port))
                }
                socket = s
                val buf = ByteArray(1024)
                while (running) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        s.receive(pkt)
                        val line = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                        GpsData.decode(line)?.let(onData)
                    } catch (e: SocketTimeoutException) {
                        // 계속 대기
                    }
                }
            } catch (e: Exception) {
                if (running) onError("${e.javaClass.simpleName}: ${e.message ?: "(메시지 없음)"}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        thread = null
    }
}
