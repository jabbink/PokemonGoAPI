package ink.abb.pogo.api

import POGOProtos.Data.PlayerDataOuterClass
import POGOProtos.Networking.Envelopes.SignatureOuterClass
import POGOProtos.Networking.Responses.GetPlayerProfileResponseOuterClass
import ink.abb.pogo.api.cache.Inventory
import ink.abb.pogo.api.cache.Map
import ink.abb.pogo.api.network.ServerRequest

interface PoGoApi {
    var latitude: Double
    var longitude: Double
    var altitude: Double
    val startTime: Long

    val deviceInfo: SignatureOuterClass.Signature.DeviceInfo

    fun currentTimeMillis(): Long
    val sessionHash: ByteArray


    fun handleResponse(serverRequest: ServerRequest)

    // caches
    var playerProfile: GetPlayerProfileResponseOuterClass.GetPlayerProfileResponse
    var playerData: PlayerDataOuterClass.PlayerData
    var map: Map
    var inventory: Inventory
}