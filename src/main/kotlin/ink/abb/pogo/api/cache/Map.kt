package ink.abb.pogo.api.cache

import com.google.common.geometry.MutableInteger
import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2LatLng
import java.util.*

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
            getMapCell(it).pokemon
        }
    }

    fun getMapCell(cellId: Long): MapCell {
        return mapCells.getOrPut(cellId, { MapCell(cellId) })
    }

    fun setPokemon(cellId: Long, mapPokemon: Set<MapPokemon>) {
        val mapCell = getMapCell(cellId)
        // Fully override Pokemon as they might've expired
        mapCell.pokemon = mapPokemon
    }

    fun setPokestops(cellId: Long, pokestops: Collection<Pokestop>) {
        val mapCell = getMapCell(cellId)
        mapCell.pokestops.addAll(pokestops)
    }

    fun setGyms(cellId: Long, gyms: Collection<Gym>) {
        val mapCell = getMapCell(cellId)
        mapCell.gyms.addAll(gyms)
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

            val halfWidth = Math.floor((width / 2).toDouble()).toInt()
            for (x in -halfWidth..halfWidth) {
                for (y in -halfWidth..halfWidth) {
                    cells.add(S2CellId.fromFaceIJ(face, i.intValue() + x * size, j.intValue() + y * size).parent(15).id())
                }
            }
            return cells
        }
    }
}