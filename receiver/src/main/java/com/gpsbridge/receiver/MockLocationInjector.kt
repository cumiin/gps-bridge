package com.gpsbridge.receiver

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import com.gpsbridge.common.GpsData

/**
 * 받은 위치를 시스템의 모의 위치(Test Provider)로 주입한다.
 * 반드시 개발자 옵션 → "모의 위치 앱"에서 이 앱을 선택해야 동작한다.
 */
class MockLocationInjector(context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )
    private var ready = false

    /** @return 성공 여부. SecurityException 이면 모의 위치 앱으로 지정되지 않은 상태. */
    fun start(): Boolean {
        return try {
            for (p in providers) {
                try { lm.removeTestProvider(p) } catch (_: Exception) {}
                lm.addTestProvider(
                    p,
                    false, false, false, false,
                    true, true, true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                lm.setTestProviderEnabled(p, true)
            }
            ready = true
            true
        } catch (e: SecurityException) {
            ready = false
            false
        } catch (e: Exception) {
            ready = false
            false
        }
    }

    fun push(d: GpsData) {
        if (!ready) return
        for (p in providers) {
            val loc = Location(p).apply {
                latitude = d.lat
                longitude = d.lon
                altitude = d.alt
                accuracy = if (d.accuracy > 0f) d.accuracy else 3f
                speed = d.speed
                bearing = d.bearing
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 1f
                    speedAccuracyMetersPerSecond = 1f
                    verticalAccuracyMeters = 1f
                }
            }
            try { lm.setTestProviderLocation(p, loc) } catch (_: Exception) {}
        }
    }

    fun stop() {
        for (p in providers) {
            try { lm.setTestProviderEnabled(p, false) } catch (_: Exception) {}
            try { lm.removeTestProvider(p) } catch (_: Exception) {}
        }
        ready = false
    }
}
