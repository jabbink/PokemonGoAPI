package ink.abb.pogo.api.cache

import POGOProtos.Data.PokemonDataOuterClass
import ink.abb.pogo.api.PoGoApi

class BagPokemon(val poGoApi: PoGoApi, rawData: PokemonDataOuterClass.PokemonDataOrBuilder) {
    val pokemonData: PokemonDataOuterClass.PokemonData.Builder =
            rawData as? PokemonDataOuterClass.PokemonData.Builder ?: PokemonDataOuterClass.PokemonData.newBuilder(rawData as? PokemonDataOuterClass.PokemonData)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BagPokemon) return false

        if (pokemonData.id != other.pokemonData.id) return false

        return true
    }

    override fun hashCode(): Int {
        return pokemonData.id.hashCode()
    }
}
