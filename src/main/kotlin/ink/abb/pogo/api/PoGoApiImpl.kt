package ink.abb.pogo.api

import POGOProtos.Networking.Envelopes.SignatureOuterClass
import ink.abb.pogo.api.auth.CredentialProvider
import ink.abb.pogo.api.auth.PtcCredentialProvider
import ink.abb.pogo.api.network.ActionQueue
import ink.abb.pogo.api.network.ServerRequest
import ink.abb.pogo.api.request.DownloadSettings
import ink.abb.pogo.api.request.GetPlayer
import ink.abb.pogo.api.util.SystemTimeImpl
import ink.abb.pogo.api.util.Time
import okhttp3.OkHttpClient
import rx.Observable
import rx.subjects.ReplaySubject
import java.util.*

class PoGoApiImpl(okHttpClient: OkHttpClient, val credentialProvider: CredentialProvider, val time: Time, override val deviceInfo: SignatureOuterClass.Signature.DeviceInfo) : PoGoApi {

    override fun currentTimeMillis(): Long {
        return time.currentTimeMillis()
    }

    override var latitude: Double = 0.0
    override var longitude: Double = 0.0
    override var altitude: Double = 0.0
    override val sessionHash = ByteArray(32)

    init {
        Random().nextBytes(sessionHash)
    }

    val actionQueue = ActionQueue(this, okHttpClient, credentialProvider)

    override var startTime = time.currentTimeMillis()

    fun <T : ServerRequest> queueRequest(request: T): Observable<T> {
        val replaySubject = ReplaySubject.create<T>()
        actionQueue.requestQueue.offer(Pair(request as ServerRequest, replaySubject as ReplaySubject<ServerRequest>))
        return replaySubject.asObservable()
    }

    // expensive IO function
    fun start() {
        credentialProvider.login()
        actionQueue.start()
        val getPlayer = GetPlayer("0.33.1")
        val settings = DownloadSettings()
        queueRequest(getPlayer).subscribe {
            println(it.response)
        }
        queueRequest(settings).subscribe {
            println(it.response)
        }
    }
}

fun main(args: Array<String>) {
    val okHttpClient = OkHttpClient()
    val time = SystemTimeImpl()
    val credentialProvider = PtcCredentialProvider(okHttpClient, "", "", time)
    val api = PoGoApiImpl(okHttpClient, credentialProvider, time, SignatureOuterClass.Signature.DeviceInfo.newBuilder().build())

    api.latitude = 50.0
    api.longitude = 4.0
    api.altitude = 35.2

    api.start()

    /*val req = GetMapObjects(api)
    api.queueRequest(req).subscribe {
        val response = it.response
        println(response)
    }

    val req2 = GetMapObjects(api)
    api.queueRequest(req2).subscribe {
        val response = it.response
        println(response)
    }*/

    Thread.sleep(100 * 1000)
}