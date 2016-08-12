package ink.abb.pogo.api

import POGOProtos.Networking.Envelopes.SignatureOuterClass

interface PoGoApi {
    var latitude: Double
    var longitude: Double
    var altitude: Double
    val startTime: Long

    val deviceInfo: SignatureOuterClass.Signature.DeviceInfo

    fun currentTimeMillis(): Long
    val sessionHash: ByteArray
}