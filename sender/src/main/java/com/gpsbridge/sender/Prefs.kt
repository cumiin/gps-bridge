package com.gpsbridge.sender

import android.content.Context
import com.gpsbridge.common.Protocol

/** 마지막 설정을 기억해 다음 실행 때 그대로 쓰도록 한다. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("gpsbridge", Context.MODE_PRIVATE)

    var mode: String
        get() = sp.getString(KEY_MODE, GpsSenderService.MODE_WIFI) ?: GpsSenderService.MODE_WIFI
        set(v) = sp.edit().putString(KEY_MODE, v).apply()

    var host: String
        get() = sp.getString(KEY_HOST, Protocol.DEFAULT_BROADCAST) ?: Protocol.DEFAULT_BROADCAST
        set(v) = sp.edit().putString(KEY_HOST, v).apply()

    var port: Int
        get() = sp.getInt(KEY_PORT, Protocol.DEFAULT_UDP_PORT)
        set(v) = sp.edit().putInt(KEY_PORT, v).apply()

    var btMac: String
        get() = sp.getString(KEY_BT_MAC, "") ?: ""
        set(v) = sp.edit().putString(KEY_BT_MAC, v).apply()

    var autoStart: Boolean
        get() = sp.getBoolean(KEY_AUTO_START, false)
        set(v) = sp.edit().putBoolean(KEY_AUTO_START, v).apply()

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_BT_MAC = "btMac"
        const val KEY_AUTO_START = "autoStart"
    }
}
