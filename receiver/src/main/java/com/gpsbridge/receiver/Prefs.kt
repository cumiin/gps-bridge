package com.gpsbridge.receiver

import android.content.Context
import com.gpsbridge.common.Protocol

/** 마지막 설정을 기억해 다음 실행 때 그대로 쓰도록 한다. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("gpsbridge", Context.MODE_PRIVATE)

    var mode: String
        get() = sp.getString(KEY_MODE, GpsReceiverService.MODE_WIFI)
            ?: GpsReceiverService.MODE_WIFI
        set(v) = sp.edit().putString(KEY_MODE, v).apply()

    var port: Int
        get() = sp.getInt(KEY_PORT, Protocol.DEFAULT_UDP_PORT)
        set(v) = sp.edit().putInt(KEY_PORT, v).apply()

    var autoStart: Boolean
        get() = sp.getBoolean(KEY_AUTO_START, false)
        set(v) = sp.edit().putBoolean(KEY_AUTO_START, v).apply()

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_PORT = "port"
        const val KEY_AUTO_START = "autoStart"
    }
}
