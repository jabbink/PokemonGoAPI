package ink.abb.pogo.api.cache

import POGOProtos.Map.Fort.FortDataOuterClass
import ink.abb.pogo.api.PoGoApi

class Gym(poGoApi: PoGoApi, rawData: FortDataOuterClass.FortData) : Fort(poGoApi, rawData) {

}