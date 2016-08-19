package ink.abb.pogo.api.cache

import POGOProtos.Enums.PokemonIdOuterClass
import POGOProtos.Map.Fort.FortDataOuterClass
import POGOProtos.Map.Pokemon.MapPokemonOuterClass
import POGOProtos.Map.Pokemon.WildPokemonOuterClass
import ink.abb.pogo.api.util.Time

class MapPokemon {
    lateinit private var encounterKind: EncounterKind

    lateinit private var spawnPointId: String

    var encounterId: Long = 0

    lateinit private var pokemonId: PokemonIdOuterClass.PokemonId

    private var pokemonIdValue: Int = 0

    private var expirationTimestampMs: Long = 0

    private var latitude: Double = 0.0

    private var longitude: Double = 0.0

    private val time: Time

    val valid: Boolean
        get() = time.currentTimeMillis() < expirationTimestampMs

    constructor(proto: MapPokemonOuterClass.MapPokemon, time: Time) {
        this.encounterKind = EncounterKind.NORMAL;
        this.spawnPointId = proto.getSpawnPointId();
        this.encounterId = proto.getEncounterId();
        this.pokemonId = proto.getPokemonId();
        this.pokemonIdValue = proto.getPokemonIdValue();
        this.expirationTimestampMs = proto.getExpirationTimestampMs();
        this.latitude = proto.getLatitude();
        this.longitude = proto.getLongitude();
        this.time = time

    }
    constructor(proto: WildPokemonOuterClass.WildPokemon, time: Time) {
        this.encounterKind = EncounterKind.NORMAL;
        this.spawnPointId = proto.getSpawnPointId();
        this.encounterId = proto.getEncounterId();
        this.pokemonId = proto.getPokemonData().getPokemonId();
        this.pokemonIdValue = proto.getPokemonData().getPokemonIdValue();
        this.expirationTimestampMs = proto.getTimeTillHiddenMs().toLong();
        this.latitude = proto.getLatitude();
        this.longitude = proto.getLongitude();
        this.time = time

    }
    constructor(proto: FortDataOuterClass.FortData, time: Time) {
        this.spawnPointId = proto.getLureInfo().getFortId();
        this.encounterId = proto.getLureInfo().getEncounterId();
        this.pokemonId = proto.getLureInfo().getActivePokemonId();
        this.pokemonIdValue = proto.getLureInfo().getActivePokemonIdValue();
        this.expirationTimestampMs = proto.getLureInfo()
                .getLureExpiresTimestampMs();
        this.latitude = proto.getLatitude();
        this.longitude = proto.getLongitude();
        this.encounterKind = EncounterKind.DISK;
        this.time = time
    }


    private enum class EncounterKind {
        NORMAL,
        DISK
    }

    override fun toString(): String{
        return "MapPokemon(encounterKind=$encounterKind, spawnPointId='$spawnPointId', encounterId=$encounterId, pokemonId=$pokemonId, pokemonIdValue=$pokemonIdValue, expirationTimestampMs=$expirationTimestampMs, latitude=$latitude, longitude=$longitude)"
    }


}