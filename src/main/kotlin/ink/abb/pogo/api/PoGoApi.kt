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
    var altitude: Double

    val startTime: Long

    fun start();

    val deviceInfo: SignatureOuterClass.Signature.DeviceInfo

    fun currentTimeMillis(): Long
    val sessionHash: ByteArray


    fun handleResponse(serverRequest: ServerRequest)

    fun <T : ServerRequest> handleRequest(serverRequest: T): T

    fun <T : ServerRequest> queueRequest(request: T): Observable<T>

    // caches
    var playerProfile: GetPlayerProfileResponseOuterClass.GetPlayerProfileResponse.Builder
    var playerData: PlayerDataOuterClass.PlayerData.Builder
    var map: Map
    var inventory: Inventory
    var fortSettings: FortSettingsOuterClass.FortSettings
    var inventorySettings: InventorySettingsOuterClass.InventorySettings
    var levelSettings: LevelSettingsOuterClass.LevelSettings
    var mapSettings: MapSettingsOuterClass.MapSettings

    val initialized: Boolean

    fun setLocation(latitude: Double, longitude: Double, altitude: Double) {
        this.latitude = latitude
        this.longitude = longitude
        this.altitude = altitude
    }
}