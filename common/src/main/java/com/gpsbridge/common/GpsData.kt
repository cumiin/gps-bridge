package com.gpsbridge.common

/**
 * 한 번의 위치 정보. 전송 시 콤마 구분 한 줄 텍스트로 직렬화한다.
 *   GPSB,위도,경도,고도,정확도,속도,방위,시간(ms)
 */
data class GpsData(
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val time: Long
) {
    fun encode(): String =
        "${Protocol.MAGIC},$lat,$lon,$alt,$accuracy,$speed,$bearing,$time"

    companion object {
        fun decode(line: String): GpsData? {
            val p = line.trim().split(",")
            if (p.size < 8 || p[0] != Protocol.MAGIC) return null
            return try {
                GpsData(
                    lat = p[1].toDouble(),
                    lon = p[2].toDouble(),
                    alt = p[3].toDouble(),
                    accuracy = p[4].toFloat(),
                    speed = p[5].toFloat(),
                    bearing = p[6].toFloat(),
                    time = p[7].toLong()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
