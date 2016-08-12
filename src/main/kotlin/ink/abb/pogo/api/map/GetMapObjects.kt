package ink.abb.pogo.api.map

import com.google.common.geometry.MutableInteger
import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2LatLng
import ink.abb.pogo.api.PoGoApi
import ink.abb.pogo.api.request.GetMapObjects
import java.util.*

class GetMapObjects(poGoApi: PoGoApi, width: Int = 3) : GetMapObjects() {
    init {
        getCellIds(poGoApi.latitude, poGoApi.longitude, width).forEach { this.withCellId(it) }
    }

    fun getCellIds(latitude: Double, longitude: Double, width: Int): List<Long> {
        val latLng = S2LatLng.fromDegrees(latitude, longitude)
        val cellId = S2CellId.fromLatLng(latLng).parent(15)

        val index = MutableInteger(0)
        val jindex = MutableInteger(0)


        val level = cellId.level()
        val size = 1 shl S2CellId.MAX_LEVEL - level
        val face = cellId.toFaceIJOrientation(index, jindex, null)

        val cells = ArrayList<Long>()

        val halfWidth = Math.floor((width / 2).toDouble()).toInt()
        for (x in -halfWidth..halfWidth) {
            for (y in -halfWidth..halfWidth) {
                cells.add(S2CellId.fromFaceIJ(face, index.intValue() + x * size, jindex.intValue() + y * size).parent(15).id())
            }
        }
        return cells
    }

}