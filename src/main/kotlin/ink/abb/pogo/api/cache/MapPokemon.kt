package ink.abb.pogo.api.cache

import POGOProtos.Enums.PokemonIdOuterClass
import POGOProtos.Map.Fort.FortDataOuterClass
import POGOProtos.Map.Pokemon.MapPokemonOuterClass
import POGOProtos.Map.Pokemon.WildPokemonOuterClass

class MapPokemon {
    lateinit private var encounterKind: EncounterKind

    lateinit private var spawnPointId: String

    var encounterId: Long = 0

    lateinit private var pokemonId: PokemonIdOuterClass.PokemonId

    private var pokemonIdValue: Int = 0

    private var expirationTimestampMs: Long = 0

    private var latitude: Double = 0.0

    private var longitude: Double = 0.0

    constructor(proto: MapPokemonOuterClass.MapPokemon) {
        this.encounterKind = EncounterKind.NORMAL;
        this.spawnPointId = proto.getSpawnPointId();
        this.encounterId = proto.getEncounterId();
        this.pokemonId = proto.getPokemonId();
        this.pokemonIdValue = proto.getPokemonIdValue();
        this.expirationTimestampMs = proto.getExpirationTimestampMs();
        this.latitude = proto.getLatitude();
        this.longitude = proto.getLongitude();

    }
    constructor(proto: WildPokemonOuterClass.WildPokemon) {
        this.encounterKind = EncounterKind.NORMAL;
        this.spawnPointId = proto.getSpawnPointId();
        this.encounterId = proto.getEncounterId();
        this.pokemonId = proto.getPokemonData().getPokemonId();
        this.pokemonIdValue = proto.getPokemonData().getPokemonIdValue();
        this.expirationTimestampMs = proto.getTimeTillHiddenMs().toLong();
        this.latitude = proto.getLatitude();
        this.longitude = proto.getLongitude();

    }
    constructor(proto: FortDataOuterClass.FortData) {
        this.spawnPointId = proto.getLureInfo().getFortId();
        this.encounterId = proto.getLureInfo().getEncounterId();
        this.pokemonId = proto.getLureInfo().getActivePokemonId();
        this.pokemonIdValue = proto.getLureInfo().getActivePokemonIdValue();
        this.expirationTimestampMs = proto.getLureInfo()
                .getLureExpiresTimestampMs();
        this.latitude = proto.getLatitude();
        this.longitude = proto.getLongitude();
        this.encounterKind = EncounterKind.DISK;
    }


    private enum class EncounterKind {
        NORMAL,
        DISK
    }
}