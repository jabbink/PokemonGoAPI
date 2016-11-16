package ink.abb.pogo.api.cache

import com.google.common.geometry.MutableInteger
import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2LatLng
import com.google.protobuf.ProtocolStringList
import java.util.*
import java.util.concurrent.TimeUnit

class Map {

    val mapCells = mutableMapOf<Long, MapCell>()

    fun getPokestops(latitude: Double, longitude: Double, width: Int = 3): List<Pokestop> {
        return getCellIds(latitude, longitude, width).flatMap {
            getMapCell(it).pokestops
        }
    }

    fun getGyms(latitude: Double, longitude: Double, width: Int = 3): List<Gym> {
        return getCellIds(latitude, longitude, width).flatMap {
            getMapCell(it).gyms
        }
    }

    fun getPokemon(latitude: Double, longitude: Double, width: Int = 3): List<MapPokemon> {
        return getCellIds(latitude, longitude, width).flatMap {
            getMapCell(it).pokemon.filter { it.valid }
        }
    }

    fun getMapCell(cellId: Long): MapCell {
        return mapCells.getOrPut(cellId, { MapCell(cellId) })
    }

    fun setPokemon(cellId: Long, time: Long, mapPokemon: Set<MapPokemon>) {
        val mapCell = getMapCell(cellId)
        /*if (time >= mapCell.lastUpdated + TimeUnit.HOURS.toMillis(1)) {
            mapCell.pokemon = mutableSetOf()
        }*/
        mapCell.lastUpdated = time
        // only keep valids and ones that we did not just receive new info about
        mapCell.pokemon = (mapCell.pokemon.filter { it.valid && !mapPokemon.contains(it) }.toSet() + mapPokemon).toMutableSet()
    }

    fun setPokestops(cellId: Long, pokestops: Collection<Pokestop>) {
        val mapCell = getMapCell(cellId)
        mapCell.pokestops.addAll(pokestops)
    }

    fun setGyms(cellId: Long, gyms: Collection<Gym>) {
        val mapCell = getMapCell(cellId)
        mapCell.gyms.addAll(gyms)
    }

    fun removeObjects(cellId: Long, deletedObjectsList: ProtocolStringList) {
        val mapCell = getMapCell(cellId)
        deletedObjectsList.forEach {
            if (it != null) {
                val objectId = it
                val deleteStop = mapCell.pokestops.find { it.fortData.id == objectId }
                if (deleteStop != null) {
                    mapCell.pokestops.remove(deleteStop)
                }
                val deleteGym = mapCell.gyms.find { it.fortData.id == objectId }
                if (deleteGym != null) {
                    mapCell.gyms.remove(deleteGym)
                }
                val deletePokemon = mapCell.pokemon.find { it.spawnPointId == objectId }
                if (deletePokemon != null) {
                    mapCell.pokemon.remove(deletePokemon)
                }
            }
        }
    }

    companion object {
        fun getCellIds(latitude: Double, longitude: Double, width: Int): List<Long> {
            val latLng = S2LatLng.fromDegrees(latitude, longitude)
            val cellId = S2CellId.fromLatLng(latLng).parent(15)

            val i = MutableInteger(0)
            val j = MutableInteger(0)

            val level = cellId.level()
            val size = 1 shl S2CellId.MAX_LEVEL - level
            val face = cellId.toFaceIJOrientation(i, j, null)

            val cells = ArrayList<Long>()

            val halfWidth = Math.floor(width.toDouble() / 2.0).toInt()
            for (x in -halfWidth..halfWidth) {
                for (y in -halfWidth..halfWidth) {
                    cells.add(S2CellId.fromFaceIJ(face, i.intValue() + x * size, j.intValue() + y * size).parent(15).id())
                }
            }
            return cells
        }
    }
}