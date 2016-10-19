package ink.abb.pogo.api

import POGOProtos.Data.PlayerDataOuterClass
import POGOProtos.Data.PokemonDataOuterClass
import POGOProtos.Enums.PokemonIdOuterClass
import POGOProtos.Enums.TutorialStateOuterClass
import POGOProtos.Inventory.EggIncubatorOuterClass
import POGOProtos.Map.Fort.FortTypeOuterClass.FortType
import POGOProtos.Networking.Envelopes.SignatureOuterClass
import POGOProtos.Networking.Requests.Messages.*
import POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType
import POGOProtos.Networking.Responses.*
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus
import POGOProtos.Networking.Responses.FortSearchResponseOuterClass.FortSearchResponse
import POGOProtos.Settings.FortSettingsOuterClass
import POGOProtos.Settings.InventorySettingsOuterClass
import POGOProtos.Settings.LevelSettingsOuterClass
import POGOProtos.Settings.MapSettingsOuterClass
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
import ink.abb.pogo.api.util.*
import okhttp3.OkHttpClient
import rx.Observable
import rx.subjects.ReplaySubject
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.fixedRateTimer

class PoGoApiImpl(okHttpClient: OkHttpClient, val credentialProvider: CredentialProvider, val time: Time) : PoGoApi {
    override var inventory: Inventory = Inventory(this)
    override var map: Map = Map()
    lateinit override var playerData: PlayerDataOuterClass.PlayerData
    lateinit override var playerProfile: GetPlayerProfileResponseOuterClass.GetPlayerProfileResponse

    lateinit override var fortSettings: FortSettingsOuterClass.FortSettings
    lateinit override var inventorySettings: InventorySettingsOuterClass.InventorySettings
    lateinit override var levelSettings: LevelSettingsOuterClass.LevelSettings
    lateinit override var mapSettings: MapSettingsOuterClass.MapSettings

    private var _initialized = false
    override val initialized: Boolean
        get() = _initialized

    override fun currentTimeMillis(): Long {
        return time.currentTimeMillis()
    }

    override var latitude: Double = 0.0
    override var longitude: Double = 0.0
    override var altitude: Double = 0.0

    override val sessionHash = ByteArray(16)
    override val deviceInfo: SignatureOuterClass.Signature.DeviceInfo

    init {
        Random().nextBytes(sessionHash)
        deviceInfo = DeviceInfoGenerator.getDeviceInfo("PokemonGoBot-${credentialProvider.hashCode()}".hashCode().toLong()).build()
    }

    val actionQueue = ActionQueue(this, okHttpClient, credentialProvider, deviceInfo)

    override var startTime = time.currentTimeMillis()

    override fun <T : ServerRequest> queueRequest(request: T): Observable<T> {
        val replaySubject = ReplaySubject.create<T>()
        actionQueue.requestQueue.offer(Pair(request as ServerRequest, replaySubject as ReplaySubject<ServerRequest>))
        return replaySubject.asObservable()
    }

    // expensive IO function
    fun start() {
        credentialProvider.login()
        actionQueue.start()
        // TODO: correct?
        val getPlayer = GetPlayer(GetPlayerMessageOuterClass.GetPlayerMessage.PlayerLocale.newBuilder().build())
        val settings = DownloadSettings()
        val inventory = GetInventory().withLastTimestampMs(0)
        val poGoApi = this
        queueRequest(getPlayer)
        queueRequest(settings).subscribe {
            val minRefresh = it.response.settings.mapSettings.getMapObjectsMinRefreshSeconds
            val maxRefresh = it.response.settings.mapSettings.getMapObjectsMaxRefreshSeconds
            val refresh = Math.min((maxRefresh - minRefresh) / 2 + minRefresh, minRefresh * 2)
            fixedRateTimer(name = "GetMapObjects", daemon = true, initialDelay = 1000L, period = TimeUnit.SECONDS.toMillis(refresh.toLong()), action = {
                if (!(poGoApi.latitude == 0.0 && poGoApi.longitude == 0.0)) {
                    queueRequest(GetMapObjects(poGoApi))
                }
            })
        }
        queueRequest(inventory).subscribe {
            _initialized = true
        }
    }

    override fun handleRequest(serverRequest: ServerRequest) {
        when (serverRequest.getRequestType()) {
            else -> {
            }
        }
    }

    override fun handleResponse(serverRequest: ServerRequest) {
        when (serverRequest.getRequestType()) {
            RequestType.DOWNLOAD_SETTINGS -> {
                val response = serverRequest.response as DownloadSettingsResponseOuterClass.DownloadSettingsResponse
                val settings = response.settings
                this.fortSettings = settings.fortSettings
                this.inventorySettings = settings.inventorySettings
                this.levelSettings = settings.levelSettings
                this.mapSettings = settings.mapSettings
                actionQueue.rateLimits.put(RequestType.GET_MAP_OBJECTS, mapSettings.getMapObjectsMinRefreshSeconds.toInt() * 1000)
                mapSettings.googleMapsApiKey
            }
            RequestType.GET_PLAYER -> {
                val response = serverRequest.response as GetPlayerResponseOuterClass.GetPlayerResponse
                this.playerData = response.playerData
                val tut = MarkTutorialComplete().withSendMarketingEmails(false).withSendPushNotifications(false)
                if (!playerData.tutorialStateList.contains(TutorialStateOuterClass.TutorialState.LEGAL_SCREEN)) {
                    tut.withTutorialsCompleted(TutorialStateOuterClass.TutorialState.LEGAL_SCREEN)
                }
                if (!playerData.tutorialStateList.contains(TutorialStateOuterClass.TutorialState.POKEMON_CAPTURE)) {
                    // No starter selected; get that Pikachu
                    val encounterTut = EncounterTutorialComplete().withPokemonId(PokemonIdOuterClass.PokemonId.PIKACHU)
                    queueRequest(encounterTut)

                    tut.withTutorialsCompleted(TutorialStateOuterClass.TutorialState.POKEMON_CAPTURE)
                            .withTutorialsCompleted(TutorialStateOuterClass.TutorialState.POKEMON_BERRY)
                }
                if (!playerData.tutorialStateList.contains(TutorialStateOuterClass.TutorialState.USE_ITEM)) {
                    tut.withTutorialsCompleted(TutorialStateOuterClass.TutorialState.USE_ITEM)
                }
                if (!playerData.tutorialStateList.contains(TutorialStateOuterClass.TutorialState.POKESTOP_TUTORIAL)) {
                    tut.withTutorialsCompleted(TutorialStateOuterClass.TutorialState.POKESTOP_TUTORIAL)
                }
                if (!playerData.tutorialStateList.contains(TutorialStateOuterClass.TutorialState.ACCOUNT_CREATION)) {
                    tut.withTutorialsCompleted(TutorialStateOuterClass.TutorialState.ACCOUNT_CREATION)
                }
                if (!playerData.tutorialStateList.contains(TutorialStateOuterClass.TutorialState.FIRST_TIME_EXPERIENCE_COMPLETE)) {
                    tut.withTutorialsCompleted(TutorialStateOuterClass.TutorialState.FIRST_TIME_EXPERIENCE_COMPLETE)
                }
                if (initialized && this.inventory.playerStats.level >= 5 && !playerData.tutorialStateList.contains(TutorialStateOuterClass.TutorialState.GYM_TUTORIAL)) {
                    tut.withTutorialsCompleted(TutorialStateOuterClass.TutorialState.GYM_TUTORIAL)
                }
                /*if (playerData.tutorialStateCount == 0) {
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
                }*/
                if (tut.getBuilder().tutorialsCompletedCount > 0) {
                    queueRequest(tut)
                    queueRequest(GetPlayer())
                }
                playerData.currenciesList.forEach {
                    this.inventory.currencies.getOrPut(it.name, { AtomicInteger(0) }).set(it.amount)
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
                        Gym(this, it)
                    }
                    val pokestops = it.fortsList.filter { it.type == FortType.CHECKPOINT }.map {
                        Pokestop(this, it)
                    }
                    val mapPokemon = pokestops.filter { it.fortData.hasLureInfo() }.map { MapPokemon(this, it.fortData) }
                            .union(
                                    it.catchablePokemonsList.map {
                                        MapPokemon(this, it)
                                    })
                            .union(it.wildPokemonsList.map {
                                MapPokemon(this, it)
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
                if (fort != null && response.name.isNotBlank()) {
                    fort.fetchedDetails = true
                    fort._name = response.name
                    fort._description = response.description
                }
            }
            RequestType.CATCH_POKEMON -> {
                val response = serverRequest.response as CatchPokemonResponseOuterClass.CatchPokemonResponse
                val status = response.status
                if (status != CatchStatus.CATCH_ERROR) {
                    val builder = serverRequest.getBuilder() as CatchPokemonMessageOuterClass.CatchPokemonMessageOrBuilder
                    this.inventory.items.getOrPut(builder.pokeball, { AtomicInteger(0) }).andDecrement
                }
            }
            RequestType.COLLECT_DAILY_DEFENDER_BONUS -> {
                val response = serverRequest.response as CollectDailyDefenderBonusResponseOuterClass.CollectDailyDefenderBonusResponse
                response.currencyTypeList.withIndex().forEach {
                    val currencyType = it.value
                    this.inventory.currencies.getOrPut(currencyType, { AtomicInteger(0) }).addAndGet(response.currencyAwardedList[it.index])
                }
            }
            RequestType.EVOLVE_POKEMON -> {
                val response = serverRequest.response as EvolvePokemonResponseOuterClass.EvolvePokemonResponse
                val result = response.result
                if (result == EvolvePokemonResponseOuterClass.EvolvePokemonResponse.Result.SUCCESS) {

                    val builder = serverRequest.getBuilder() as EvolvePokemonMessageOuterClass.EvolvePokemonMessageOrBuilder

                    val meta = PokemonMetaRegistry.getMeta(response.evolvedPokemonData.pokemonId)
                    this.inventory.candies.getOrPut(meta.family, { AtomicInteger(0) }).addAndGet(response.candyAwarded - meta.candyToEvolve)
                    this.inventory.pokemon.remove(builder.pokemonId)
                    this.inventory.pokemon.put(response.evolvedPokemonData.id, BagPokemon(this, response.evolvedPokemonData))
                }
            }
            RequestType.FORT_SEARCH -> {
                val response = serverRequest.response as FortSearchResponse
                val builder = serverRequest.getBuilder() as FortSearchMessageOuterClass.FortSearchMessageOrBuilder
                val cellId = S2CellId.fromLatLng(S2LatLng.fromDegrees(builder.fortLatitude, builder.fortLongitude)).parent(15).id()
                val mapCell = map.getMapCell(cellId)
                val pokestop = mapCell.pokestops.find { it.id == builder.fortId }
                if (response.result == FortSearchResponse.Result.INVENTORY_FULL || response.result == FortSearchResponse.Result.SUCCESS) {
                    this.inventory.gems.addAndGet(response.gemsAwarded)
                    response.itemsAwardedList.forEach {
                        this.inventory.items.getOrPut(it.itemId, { AtomicInteger(0) }).addAndGet(it.itemCount)
                    }
                    if (pokestop != null) {
                        pokestop.cooldownCompleteTimestampMs = response.cooldownCompleteTimestampMs
                    }
                    // TODO: Can't change experience here...
                } else if (response.result == FortSearchResponse.Result.IN_COOLDOWN_PERIOD) {
                    if (pokestop != null) {
                        if (response.cooldownCompleteTimestampMs > currentTimeMillis()) {
                            pokestop.cooldownCompleteTimestampMs = response.cooldownCompleteTimestampMs
                        } else {
                            pokestop.cooldownCompleteTimestampMs = currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
                        }
                    }
                }
            }
            RequestType.ADD_FORT_MODIFIER -> {
                val response = serverRequest.response as AddFortModifierResponseOuterClass.AddFortModifierResponse
                if (response.result == AddFortModifierResponseOuterClass.AddFortModifierResponse.Result.SUCCESS) {
                    val builder = serverRequest.getBuilder() as AddFortModifierMessageOuterClass.AddFortModifierMessageOrBuilder
                    this.inventory.items.getOrPut(builder.modifierType, { AtomicInteger(0) }).andDecrement
                    // TODO: Need map update...
                }
            }
            RequestType.GET_HATCHED_EGGS -> {
                val response = serverRequest.response as GetHatchedEggsResponseOuterClass.GetHatchedEggsResponse
                if (response.success) {
                    response.candyAwardedList.withIndex().forEach {
                        val pokemonId = response.getPokemonId(it.index)
                        val pokemon = this.inventory.pokemon[pokemonId]
                        val egg = this.inventory.eggs[pokemonId]
                        if (pokemon != null) {
                            val meta = PokemonMetaRegistry.getMeta(pokemon.pokemonData.pokemonId)
                            this.inventory.candies.getOrPut(meta.family, { AtomicInteger(0) }).addAndGet(it.value)
                        }
                        if (egg != null) {
                            this.inventory.eggs.remove(pokemonId)
                            if (pokemon == null) {
                                val meta = PokemonMetaRegistry.getMeta(egg.pokemonData.pokemonId)
                                this.inventory.candies.getOrPut(meta.family, { AtomicInteger(0) }).addAndGet(it.value)
                                this.inventory.pokemon.put(pokemonId, BagPokemon(this, egg.pokemonData))
                            }
                        }
                    }
                    response.experienceAwardedList.forEach {
                        // TODO: Add experience
                    }
                    response.stardustAwardedList.forEach {
                        // TODO: Add stardust
                    }
                }
            }
            RequestType.LEVEL_UP_REWARDS -> {
                val response = serverRequest.response as LevelUpRewardsResponseOuterClass.LevelUpRewardsResponse
                if (response.result == LevelUpRewardsResponseOuterClass.LevelUpRewardsResponse.Result.SUCCESS) {
                    response.itemsAwardedList.forEach {
                        this.inventory.items.getOrPut(it.itemId, { AtomicInteger(0) }).addAndGet(it.itemCount)
                    }
                    /*response.itemsUnlockedList.forEach {

                    }*/
                }
            }
            RequestType.RECYCLE_INVENTORY_ITEM -> {
                val response = serverRequest.response as RecycleInventoryItemResponseOuterClass.RecycleInventoryItemResponse
                if (response.result == RecycleInventoryItemResponseOuterClass.RecycleInventoryItemResponse.Result.SUCCESS) {
                    val builder = serverRequest.getBuilder() as RecycleInventoryItemMessageOuterClass.RecycleInventoryItemMessageOrBuilder
                    val counter = this.inventory.items.getOrPut(builder.itemId, { AtomicInteger(response.newCount + builder.count) }).addAndGet(-builder.count)
                    if (counter != response.newCount) {
                        System.err.println("Counter for ${builder.itemId} desynced. Thought we had $counter, but have ${response.newCount}")
                        this.inventory.items.getOrPut(builder.itemId, { AtomicInteger(0) }).set(response.newCount)
                    }
                }
            }
            RequestType.RELEASE_POKEMON -> {
                val response = serverRequest.response as ReleasePokemonResponseOuterClass.ReleasePokemonResponse
                if (response.result == ReleasePokemonResponseOuterClass.ReleasePokemonResponse.Result.SUCCESS) {
                    val builder = serverRequest.getBuilder() as ReleasePokemonMessageOuterClass.ReleasePokemonMessageOrBuilder
                    val pokemon = this.inventory.pokemon.get(builder.pokemonId)
                    if (pokemon != null) {
                        val meta = PokemonMetaRegistry.getMeta(pokemon.pokemonData.pokemonId)
                        this.inventory.candies.getOrPut(meta.family, { AtomicInteger(0) }).addAndGet(response.candyAwarded)
                        this.inventory.pokemon.remove(builder.pokemonId)
                    }
                }
            }
            RequestType.SET_FAVORITE_POKEMON -> {
                val response = serverRequest.response as SetFavoritePokemonResponseOuterClass.SetFavoritePokemonResponse
                if (response.result == SetFavoritePokemonResponseOuterClass.SetFavoritePokemonResponse.Result.SUCCESS) {
                    val builder = serverRequest.getBuilder() as SetFavoritePokemonMessageOuterClass.SetFavoritePokemonMessageOrBuilder
                    val pokemon = this.inventory.pokemon.get(builder.pokemonId)
                    if (pokemon != null) {
                        val newPokemon = PokemonDataOuterClass.PokemonData.newBuilder(pokemon.pokemonData)
                        newPokemon.favorite = if (builder.isFavorite) 1 else 0
                        this.inventory.pokemon.put(newPokemon.id, BagPokemon(this, newPokemon.build()))
                    }
                }
            }
            RequestType.UPGRADE_POKEMON -> {
                val response = serverRequest.response as UpgradePokemonResponseOuterClass.UpgradePokemonResponse
                if (response.result == UpgradePokemonResponseOuterClass.UpgradePokemonResponse.Result.SUCCESS) {
                    // TODO: Need inventory update...
                }
            }
            RequestType.USE_ITEM_CAPTURE -> {
                val response = serverRequest.response as UseItemCaptureResponseOuterClass.UseItemCaptureResponse
                if (response.success) {
                    val builder = serverRequest.getBuilder() as UseItemCaptureMessageOuterClass.UseItemCaptureMessageOrBuilder
                    this.inventory.items.getOrPut(builder.itemId, { AtomicInteger(0) }).andDecrement
                }
            }
            RequestType.USE_ITEM_POTION -> {
                val response = serverRequest.response as UseItemPotionResponseOuterClass.UseItemPotionResponse
                if (response.result == UseItemPotionResponseOuterClass.UseItemPotionResponse.Result.SUCCESS) {
                    val builder = serverRequest.getBuilder() as UseItemPotionMessageOuterClass.UseItemPotionMessageOrBuilder
                    this.inventory.items.getOrPut(builder.itemId, { AtomicInteger(0) }).andDecrement
                    val pokemon = this.inventory.pokemon.get(builder.pokemonId)
                    if (pokemon != null) {
                        val newPokemon = PokemonDataOuterClass.PokemonData.newBuilder(pokemon.pokemonData)
                        newPokemon.stamina = response.stamina
                        this.inventory.pokemon.put(newPokemon.id, BagPokemon(this, newPokemon.build()))
                    }
                }
            }
            RequestType.USE_ITEM_XP_BOOST -> {
                val response = serverRequest.response as UseItemXpBoostResponseOuterClass.UseItemXpBoostResponse
                if (response.result == UseItemXpBoostResponseOuterClass.UseItemXpBoostResponse.Result.SUCCESS) {
                    val builder = serverRequest.getBuilder() as UseItemXpBoostMessageOuterClass.UseItemXpBoostMessageOrBuilder
                    this.inventory.items.getOrPut(builder.itemId, { AtomicInteger(0) }).andDecrement
                }
            }
            RequestType.USE_INCENSE -> {
                val response = serverRequest.response as UseIncenseResponseOuterClass.UseIncenseResponse
                if (response.result == UseIncenseResponseOuterClass.UseIncenseResponse.Result.SUCCESS) {
                    val item = response.appliedIncense.itemId
                    this.inventory.items.getOrPut(item, { AtomicInteger(0) }).andDecrement
                }
            }
            RequestType.USE_ITEM_EGG_INCUBATOR -> {
                val response = serverRequest.response as UseItemEggIncubatorResponseOuterClass.UseItemEggIncubatorResponse
                if (response.result == UseItemEggIncubatorResponseOuterClass.UseItemEggIncubatorResponse.Result.SUCCESS) {
                    val builder = serverRequest.getBuilder() as UseItemEggIncubatorMessageOuterClass.UseItemEggIncubatorMessageOrBuilder
                    val egg = this.inventory.eggs.get(builder.pokemonId)
                    val incubator = this.inventory.eggIncubators.get(builder.itemId)
                    if (egg != null && incubator != null) {
                        val newEgg = PokemonDataOuterClass.PokemonData.newBuilder(egg.pokemonData)
                        newEgg.eggIncubatorId = incubator.id
                        val newIncubator = EggIncubatorOuterClass.EggIncubator.newBuilder(incubator)
                        newIncubator.pokemonId = newEgg.id
                        this.inventory.eggs.put(egg.pokemonData.id, BagPokemon(this, newEgg.build()))
                        this.inventory.eggIncubators.put(incubator.id, newIncubator.build())
                    }
                }
            }
            RequestType.CHECK_CHALLENGE -> {
                val response = serverRequest.response as CheckChallengeResponseOuterClass.CheckChallengeResponse
                if (response.showChallenge) {
                    System.err.println("Received challenge request!")
                    System.err.println(response.challengeUrl)
                    System.exit(1)
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
    val okHttpClient = OkHttpClient()
    val time = SystemTimeImpl()
    val credentialProvider = PtcCredentialProvider(okHttpClient, "", "", time)

    val api = PoGoApiImpl(okHttpClient, credentialProvider, time)

    api.latitude = 52.0
    api.longitude = 4.4

    api.start()

    //Thread.sleep(1000)

    fixedRateTimer(name = "check map", daemon = true, initialDelay = 0L, period = 10000L, action = {
        println(api.map.getGyms(api.latitude, api.longitude))
        println(api.map.getPokestops(api.latitude, api.longitude))
        println(api.map.getPokemon(api.latitude, api.longitude))
    })

    /*val req = GetMapObjects(api)
    api.queueRequest(req).subscribe {
        val first = api.map.getPokestops(api.latitude, api.longitude, 9).first()
        api.queueRequest(first.getFortDetails()).subscribe {
            println(it.response)
            println(first.name)
            println(first.description)
        }
    }*/

    /*val req2 = GetMapObjects(api)
    api.queueRequest(req2).subscribe {
        val response = it.response
        println(response)
    }*/

    Thread.sleep(1000 * 1000)
}