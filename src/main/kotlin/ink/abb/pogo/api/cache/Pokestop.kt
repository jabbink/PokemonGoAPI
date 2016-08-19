package ink.abb.pogo.api.cache

import POGOProtos.Map.Fort.FortDataOuterClass

class Pokestop(fortData: FortDataOuterClass.FortData) : Fort(fortData) {
    var cooldownCompleteTimestampMs = 0L
}