package ink.abb.pogo.api.cache

class MapCell(val cellId: Long) {
    var lastUpdated = 0L

    var pokemon = mutableSetOf<MapPokemon>()
    val pokestops = mutableSetOf<Pokestop>()
    val gyms = mutableSetOf<Gym>()

    fun equals(that: MapCell): Boolean {
        return that.cellId == cellId
    }
}