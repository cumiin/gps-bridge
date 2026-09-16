package com.gpsbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.gpsbridge.common.Protocol
import java.io.OutputStream

/**
 * 페어링된 태블릿(수신 앱=SPP 서버)에 RFCOMM 으로 연결해 한 줄씩 전송.
 */
@SuppressLint("MissingPermission")
class BluetoothSender(private val device: BluetoothDevice) {

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    fun open() {
        val s = device.createRfcommSocketToServiceRecord(Protocol.BT_UUID)
        s.connect()
        socket = s
        out = s.outputStream
    }

    fun send(line: String) {
        out?.apply {
            write((line + "\n").toByteArray(Charsets.UTF_8))
            flush()
        }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        out = null
    }
}
