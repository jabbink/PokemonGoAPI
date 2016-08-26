package ink.abb.pogo.api.cache

import POGOProtos.Map.Fort.FortDataOuterClass
import ink.abb.pogo.api.PoGoApi

class Pokestop(poGoApi: PoGoApi, fortData: FortDataOuterClass.FortData) : Fort(poGoApi, fortData) {
    var cooldownCompleteTimestampMs = fortData.cooldownCompleteTimestampMs
}