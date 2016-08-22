package ink.abb.pogo.api.cache

import POGOProtos.Map.Fort.FortDataOuterClass
import ink.abb.pogo.api.PoGoApi
import ink.abb.pogo.api.request.FortDetails

abstract class Fort(val poGoApi: PoGoApi, val fortData: FortDataOuterClass.FortData) {
    val id = fortData.id

    var fetchedDetails = false

    var name: String? = null
    var description: String? = null

    fun equals(that: Fort): Boolean {
        return that.id == id
    }

    fun getFortDetails(): FortDetails {
        return FortDetails().withFortId(id).withLatitude(fortData.latitude).withLongitude(fortData.longitude)
    }

    override fun toString(): String{
        return "Fort(fortData=$fortData, id='$id', fetchedDetails=$fetchedDetails, name='$name', description=$description)"
    }
}