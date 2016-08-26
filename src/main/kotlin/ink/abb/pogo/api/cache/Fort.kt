package ink.abb.pogo.api.cache

import POGOProtos.Map.Fort.FortDataOuterClass
import ink.abb.pogo.api.PoGoApi
import ink.abb.pogo.api.request.FortDetails

abstract class Fort(val poGoApi: PoGoApi, val fortData: FortDataOuterClass.FortData) {
    val id = fortData.id

    var fetchedDetails = false

    var _name: String = ""
    val name: String
        get() {
            fetchDetails()
            return _name
        }

    var _description: String = ""
    val description: String
        get() {
            fetchDetails()
            return _description
        }

    fun equals(that: Fort): Boolean {
        return that.id == id
    }

    fun getFortDetails(): FortDetails {
        return FortDetails().withFortId(id).withLatitude(fortData.latitude).withLongitude(fortData.longitude)
    }

    fun fetchDetails() {
        if (fetchedDetails) {
            return
        }
        synchronized(fetchedDetails) {
            // maybe fetched in the meantime
            if (fetchedDetails) {
                return
            }
            val reply = poGoApi.queueRequest(getFortDetails()).toBlocking().first().response
            if (_name != reply.name || _name == "") {
                System.err.println("Failed to fetch name of $id")
                fetchedDetails = false
            } else {
                fetchedDetails = true
            }
        }
    }

    override fun toString(): String {
        return "Fort(fortData=$fortData, id='$id', fetchedDetails=$fetchedDetails, name='$name', description=$description)"
    }
}