package ink.abb.pogo.api

import POGOProtos.Data.Player.PlayerAvatarOuterClass
import POGOProtos.Data.PlayerDataOuterClass
import POGOProtos.Enums.PokemonIdOuterClass
import POGOProtos.Enums.TutorialStateOuterClass
import POGOProtos.Map.Fort.FortTypeOuterClass.FortType
import POGOProtos.Networking.Envelopes.SignatureOuterClass
import POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType
import POGOProtos.Networking.Responses.*
import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2LatLng
import ink.abb.pogo.api.auth.CredentialProvider
import ink.abb.pogo.api.auth.PtcCredentialProvider
import ink.abb.pogo.api.cache.*
import ink.abb.pogo.api.cache.Map
import ink.abb.pogo.api.map.GetMapObjects
import ink.abb.pogo.api.network.ActionQueue
import ink.abb.pogo.api.network.ServerRequest
import ink.abb.pogo.api.request.*
import ink.abb.pogo.api.util.SystemTimeImpl
import ink.abb.pogo.api.util.Time
import okhttp3.OkHttpClient
import rx.Observable
import rx.subjects.ReplaySubject
import java.util.*

class PoGoApiImpl(okHttpClient: OkHttpClient, val credentialProvider: CredentialProvider, val time: Time, override val deviceInfo: SignatureOuterClass.Signature.DeviceInfo) : PoGoApi {
    override var inventory: Inventory = Inventory()
    override var map: Map = Map()
    lateinit override var playerData: PlayerDataOuterClass.PlayerData
    lateinit override var playerProfile: GetPlayerProfileResponseOuterClass.GetPlayerProfileResponse

    override fun currentTimeMillis(): Long {
        return time.currentTimeMillis()
    }

    override var latitude: Double = 0.0
    override var longitude: Double = 0.0
    override var altitude: Double = 0.0
    override val sessionHash = ByteArray(16)

    init {
        Random().nextBytes(sessionHash)
    }

    val actionQueue = ActionQueue(this, okHttpClient, credentialProvider, deviceInfo)

    override var startTime = time.currentTimeMillis()

    fun <T : ServerRequest> queueRequest(request: T): Observable<T> {
        val replaySubject = ReplaySubject.create<T>()
        actionQueue.requestQueue.offer(Pair(request as ServerRequest, replaySubject as ReplaySubject<ServerRequest>))
        return replaySubject.asObservable()
    }

    // expensive IO function
    fun start() {
        credentialProvider.login()
        actionQueue.start()
        val getPlayer = GetPlayer("0.33.1")
        val settings = DownloadSettings()
        val inventory = GetInventory().withLastTimestampMs(0)
        queueRequest(getPlayer)
        queueRequest(settings)
        queueRequest(inventory)
    }

    override fun handleResponse(serverRequest: ServerRequest) {
        when (serverRequest.getRequestType()) {
            RequestType.GET_PLAYER -> {
                val response = serverRequest.response as GetPlayerResponseOuterClass.GetPlayerResponse
                this.playerData = response.playerData
                if (playerData.tutorialStateCount == 0) {
                    val encounterTut = EncounterTutorialComplete().withPokemonId(PokemonIdOuterClass.PokemonId.PIKACHU)
                    queueRequest(encounterTut)
                    val avatar = SetAvatar().withPlayerAvatar(PlayerAvatarOuterClass.PlayerAvatar.newBuilder().build())
                    queueRequest(avatar)
                    val tut = MarkTutorialComplete().withSendMarketingEmails(false).withSendPushNotifications(false)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.LEGAL_SCREEN)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.POKEMON_BERRY)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.POKEMON_CAPTURE)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.POKESTOP_TUTORIAL)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.ACCOUNT_CREATION)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.AVATAR_SELECTION)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.FIRST_TIME_EXPERIENCE_COMPLETE)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.NAME_SELECTION)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.USE_ITEM)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.GYM_TUTORIAL)
                    queueRequest(tut)
                }
            }
            RequestType.GET_PLAYER_PROFILE -> {
                val response = serverRequest.response as GetPlayerProfileResponseOuterClass.GetPlayerProfileResponse
                this.playerProfile = response
            }
            RequestType.GET_MAP_OBJECTS -> {
                val response = serverRequest.response as GetMapObjectsResponseOuterClass.GetMapObjectsResponse
                response.mapCellsList.forEach {
                    val gyms = it.fortsList.filter { it.type == FortType.GYM }.map {
                        Gym(it)
                    }
                    val pokestops = it.fortsList.filter { it.type == FortType.CHECKPOINT }.map {
                        Pokestop(it)
                    }
                    val mapPokemon = pokestops.filter { it.fortData.hasLureInfo() }.map { MapPokemon(it.fortData) }
                            .union(
                                    it.catchablePokemonsList.map {
                                        MapPokemon(it)
                                    })
                            .union(it.wildPokemonsList.map {
                                MapPokemon(it)
                            })
                    map.setGyms(it.s2CellId, gyms)
                    map.setPokestops(it.s2CellId, pokestops)
                    map.setPokemon(it.s2CellId, this.currentTimeMillis(), mapPokemon)
                }
            }
            RequestType.GET_INVENTORY -> {
                val response = serverRequest.response as GetInventoryResponseOuterClass.GetInventoryResponse
                inventory.update(response.inventoryDelta)
            }
            RequestType.FORT_DETAILS -> {
                val response = serverRequest.response as FortDetailsResponseOuterClass.FortDetailsResponse
                val cellId = S2CellId.fromLatLng(S2LatLng.fromDegrees(response.latitude, response.longitude)).parent(15).id()
                val mapCell = map.getMapCell(cellId)
                val fort = mapCell.pokestops.find { it.id == response.fortId } ?: mapCell.gyms.find { it.id == response.fortId }
                if (fort == null) {
                    // should never happen
                } else {
                    fort.fetchedDetails = true
                    fort.name = response.name
                    fort.description = response.description
                }
            }
            else -> {
                // Don't cache
            }
        }
        //throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

/**
 *  Set of chars for a half-byte.
 */
private val CHARS = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

/**
 *  Returns the string of two characters representing the HEX value of the byte.
 */
internal fun Byte.toHexString(): String {
    val i = this.toInt()
    val char2 = CHARS[i and 0x0f]
    val char1 = CHARS[i shr 4 and 0x0f]
    return "$char1$char2"
}

/**
 *  Returns the HEX representation of ByteArray data.
 */
internal fun ByteArray.toHexString(): String {
    val builder = StringBuilder()
    for (b in this) {
        builder.append(b.toHexString())
    }
    return builder.toString()
}

fun main(args: Array<String>) {

    val devices = arrayOf(
            Triple("iPad3,1", "iPad", "J1AP"),
            Triple("iPad3,2", "iPad", "J2AP"),
            Triple("iPad3,3", "iPad", "J2AAP"),
            Triple("iPad3,4", "iPad", "P101AP"),
            Triple("iPad3,5", "iPad", "P102AP"),
            Triple("iPad3,6", "iPad", "P103AP"),

            Triple("iPad4,1", "iPad", "J71AP"),
            Triple("iPad4,2", "iPad", "J72AP"),
            Triple("iPad4,3", "iPad", "J73AP"),
            Triple("iPad4,4", "iPad", "J85AP"),
            Triple("iPad4,5", "iPad", "J86AP"),
            Triple("iPad4,6", "iPad", "J87AP"),
            Triple("iPad4,7", "iPad", "J85mAP"),
            Triple("iPad4,8", "iPad", "J86mAP"),
            Triple("iPad4,9", "iPad", "J87mAP"),

            Triple("iPad5,1", "iPad", "J96AP"),
            Triple("iPad5,2", "iPad", "J97AP"),
            Triple("iPad5,3", "iPad", "J81AP"),
            Triple("iPad5,4", "iPad", "J82AP"),

            Triple("iPad6,7", "iPad", "J98aAP"),
            Triple("iPad6,8", "iPad", "J99aAP"),

            Triple("iPhone5,1", "iPhone", "N41AP"),
            Triple("iPhone5,2", "iPhone", "N42AP"),
            Triple("iPhone5,3", "iPhone", "N48AP"),
            Triple("iPhone5,4", "iPhone", "N49AP"),

            Triple("iPhone6,1", "iPhone", "N51AP"),
            Triple("iPhone6,2", "iPhone", "N53AP"),

            Triple("iPhone7,1", "iPhone", "N56AP"),
            Triple("iPhone7,2", "iPhone", "N61AP"),

            Triple("iPhone8,1", "iPhone", "N71AP")
    )

    val osVersions = arrayOf("8.1.1", "8.1.2", "8.1.3", "8.2", "8.3", "8.4", "8.4.1",
            "9.0", "9.0.1", "9.0.2", "9.1", "9.2", "9.2.1", "9.3", "9.3.1", "9.3.2", "9.3.3", "9.3.4")


    val okHttpClient = OkHttpClient()
    val time = SystemTimeImpl()
    val credentialProvider = PtcCredentialProvider(okHttpClient, "", "", time)

    // try to create unique identifier
    val random = Random("PokemonGoBot-${credentialProvider.hashCode()}".hashCode().toLong())
    val deviceInfo = SignatureOuterClass.Signature.DeviceInfo.newBuilder()

    val deviceId = ByteArray(16)
    random.nextBytes(deviceId)

    deviceInfo.setDeviceId(deviceId.toHexString())
    deviceInfo.setDeviceBrand("Apple")

    val device = devices[random.nextInt(devices.size)]
    deviceInfo.deviceModel = device.second
    deviceInfo.deviceModelBoot = "${device.first}${0.toChar()}"
    deviceInfo.hardwareManufacturer = "Apple"
    deviceInfo.hardwareModel = "${device.third}${0.toChar()}"
    deviceInfo.firmwareBrand = "iPhone OS"
    deviceInfo.firmwareType = osVersions[random.nextInt(osVersions.size)]

    val api = PoGoApiImpl(okHttpClient, credentialProvider, time, deviceInfo.build())

    api.latitude = 52.0
    api.longitude = 4.4
    api.altitude = -2.5

    api.start()

    //Thread.sleep(1000)

    val req = GetMapObjects(api)
    api.queueRequest(req).subscribe {
        val response = it.response
        val first = api.map.getPokestops(api.latitude, api.longitude, 9).first()
        api.queueRequest(first.getFortDetails()).subscribe {
            println(it.response)
            println(first.name)
            println(first.description)
        }
    }

    /*val req2 = GetMapObjects(api)
    api.queueRequest(req2).subscribe {
        val response = it.response
        println(response)
    }*/

    Thread.sleep(100 * 1000)
}