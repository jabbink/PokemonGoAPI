package ink.abb.pogo.api.cache

import POGOProtos.Data.PokemonDataOuterClass
import ink.abb.pogo.api.PoGoApi

class BagPokemon(val poGoApi: PoGoApi, val pokemonData: PokemonDataOuterClass.PokemonData) {
    fun equals(that: BagPokemon): Boolean {
        return that.pokemonData.id == pokemonData.id
    }
}