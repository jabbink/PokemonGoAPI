package ink.abb.pogo.api.cache

import POGOProtos.Map.Fort.FortDataOuterClass
import ink.abb.pogo.api.PoGoApi

class Pokestop(poGoApi: PoGoApi, rawData: FortDataOuterClass.FortData) : Fort(poGoApi, rawData) {
    var cooldownCompleteTimestampMs = fortData.cooldownCompleteTimestampMs
}