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
                if (port !in 1..65535) {
                    onError("잘못된 포트 값: $port")
                    return@Thread
                }
                // 포트를 직접 지정해 바인딩한다(InetSocketAddress 경유 시 포트 오류가 났던 이력 있음).
                val s = DatagramSocket(port)
                s.broadcast = true
                s.soTimeout = 1000
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
                if (running) {
                    onError("${e.javaClass.simpleName}: ${e.message ?: "(메시지 없음)"} [포트 $port]")
                }
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
