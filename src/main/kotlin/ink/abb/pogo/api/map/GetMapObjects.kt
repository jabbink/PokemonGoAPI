package ink.abb.pogo.api.map

import ink.abb.pogo.api.PoGoApi
import ink.abb.pogo.api.cache.Map
import ink.abb.pogo.api.request.GetMapObjects

class GetMapObjects(poGoApi: PoGoApi, width: Int = 3) : GetMapObjects() {
    init {
        Map.getCellIds(poGoApi.latitude, poGoApi.longitude, width).forEach {
            this.withCellId(it)
            val oldCell = poGoApi.map.mapCells.get(it)
            if (oldCell == null || oldCell.lastUpdated == 0L) {
                this.withSinceTimestampMs(0)
                //println("Requesting cell $it with lastUpdated timestampSince $timestampSince")
            } else {
                this.withSinceTimestampMs(oldCell.lastUpdated)
                //println("Requesting cell $it with lastUpdated oldValue ${oldCell.lastUpdated}")
            }
        }
    }
}