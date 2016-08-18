package ink.abb.pogo.api.cache

import POGOProtos.Map.Fort.FortDataOuterClass
import ink.abb.pogo.api.request.FortDetails

abstract class Fort(val fortData: FortDataOuterClass.FortData) {
    val id = fortData.id

    var name = ""
    var description: String = ""

    fun equals(that: Fort): Boolean {
        return that.id == id
    }

    fun getFortDetails(): FortDetails {
        return FortDetails().withFortId(id).withLatitude(fortData.latitude).withLongitude(fortData.longitude)
    }
}