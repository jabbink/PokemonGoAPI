package ink.abb.pogo.api

import POGOProtos.Data.PlayerDataOuterClass
import POGOProtos.Networking.Envelopes.SignatureOuterClass
import POGOProtos.Networking.Responses.GetPlayerProfileResponseOuterClass
import POGOProtos.Settings.FortSettingsOuterClass
import POGOProtos.Settings.InventorySettingsOuterClass
import POGOProtos.Settings.LevelSettingsOuterClass
import POGOProtos.Settings.MapSettingsOuterClass
import ink.abb.pogo.api.cache.Inventory
import ink.abb.pogo.api.cache.Map
import ink.abb.pogo.api.network.ServerRequest
import rx.Observable

interface PoGoApi {
    var latitude: Double
    var longitude: Double

    val startTime: Long

    val deviceInfo: SignatureOuterClass.Signature.DeviceInfo

    fun currentTimeMillis(): Long
    val sessionHash: ByteArray


    fun handleResponse(serverRequest: ServerRequest)

    fun handleRequest(serverRequest: ServerRequest)

    fun <T : ServerRequest> queueRequest(request: T): Observable<T>

    // caches
    var playerProfile: GetPlayerProfileResponseOuterClass.GetPlayerProfileResponse
    var playerData: PlayerDataOuterClass.PlayerData
    var map: Map
    var inventory: Inventory
    var fortSettings: FortSettingsOuterClass.FortSettings
    var inventorySettings: InventorySettingsOuterClass.InventorySettings
    var levelSettings: LevelSettingsOuterClass.LevelSettings
    var mapSettings: MapSettingsOuterClass.MapSettings

    val initialized: Boolean

    fun setLocation(latitude: Double, longitude: Double) {
        this.latitude = latitude
        this.longitude = longitude
    }
}