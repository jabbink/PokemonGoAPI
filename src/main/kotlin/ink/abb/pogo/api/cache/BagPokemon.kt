package ink.abb.pogo.api.cache

import POGOProtos.Data.PokemonDataOuterClass

class BagPokemon(val pokemonData: PokemonDataOuterClass.PokemonData) {
    fun equals(that: BagPokemon): Boolean {
        return that.pokemonData.id == pokemonData.id
    }
}