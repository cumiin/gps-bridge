package com.gpsbridge.common

import java.util.UUID

/**
 * 송신/수신 앱이 공유하는 프로토콜 상수.
 */
object Protocol {
    /** 패킷 식별 매직 헤더 */
    const val MAGIC = "GPSB"

    /** 기본 UDP 포트 */
    const val DEFAULT_UDP_PORT = 50505

    /** 기본 브로드캐스트 주소 (핫스팟/공유기 공용) */
    const val DEFAULT_BROADCAST = "255.255.255.255"

    /** 블루투스 RFCOMM(SPP) 서비스 UUID — 송/수신 동일해야 함 */
    val BT_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    /** 블루투스 서비스 이름 */
    const val BT_NAME = "GpsBridge"
}
