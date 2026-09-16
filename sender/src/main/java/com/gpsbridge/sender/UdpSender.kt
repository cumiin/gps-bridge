package com.gpsbridge.sender

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * UDP 로 위치 문자열을 송출한다.
 * host 가 255.255.255.255 이면 브로드캐스트, 특정 IP 면 유니캐스트.
 */
class UdpSender(private val host: String, private val port: Int) {

    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null

    fun open() {
        socket = DatagramSocket().apply { broadcast = true }
        address = InetAddress.getByName(host)
    }

    fun send(line: String) {
        val s = socket ?: return
        val addr = address ?: return
        val data = line.toByteArray(Charsets.UTF_8)
        s.send(DatagramPacket(data, data.size, addr, port))
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
