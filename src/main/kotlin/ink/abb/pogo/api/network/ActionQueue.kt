package ink.abb.pogo.api.network

import POGOProtos.Networking.Envelopes.AuthTicketOuterClass
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass
import POGOProtos.Networking.Envelopes.ResponseEnvelopeOuterClass
import POGOProtos.Networking.Envelopes.SignatureOuterClass
import POGOProtos.Networking.Requests.RequestOuterClass
import POGOProtos.Networking.Requests.RequestTypeOuterClass
import ink.abb.pogo.api.PoGoApi
import ink.abb.pogo.api.auth.CredentialProvider
import ink.abb.pogo.api.exceptions.LoginFailedException
import ink.abb.pogo.api.exceptions.RemoteServerException
import ink.abb.pogo.api.util.Signature
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import rx.Observable
import rx.subjects.PublishSubject
import rx.subjects.ReplaySubject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

class ActionQueue(val poGoApi: PoGoApi, val okHttpClient: OkHttpClient, val credentialProvider: CredentialProvider, val deviceInfo: SignatureOuterClass.Signature.DeviceInfo) {
    val maxItems = 10

    val queueInterval = 1000L

    val requestQueue = ConcurrentLinkedDeque<Pair<ServerRequest, ReplaySubject<ServerRequest>>>()
    val didAction = PublishSubject.create<Nothing>()

    val lastUseds = mutableMapOf<RequestTypeOuterClass.RequestType, Long>()
    val rateLimits = mutableMapOf<RequestTypeOuterClass.RequestType, Int>()

    init {
        didAction.subscribe({
            val timer = Observable.timer(queueInterval, TimeUnit.MILLISECONDS)
            timer.subscribe timerSubscribe@ {
                //println("Time start")
                if (requestQueue.isEmpty()) {
                    //println("Time end")
                    didAction.onNext(null)
                    return@timerSubscribe
                }
                var taken = 0
                val queue = mutableListOf<Pair<ServerRequest, ReplaySubject<ServerRequest>>>()
                val curTime = poGoApi.currentTimeMillis()
                while (requestQueue.isNotEmpty() && taken++ < maxItems) {
                    val next = requestQueue.peek()
                    val type = next.first.getRequestType()
                    val lastUsed = lastUseds.getOrElse(type, { 0 })
                    val rateLimit = rateLimits.getOrElse(type, { 0 })
                    if (curTime > lastUsed + rateLimit) {
                        lastUseds.put(type, curTime)
                        queue.add(requestQueue.pop())
                    }
                }

                sendRequests(queue.map { it })

                //println("Time end")
                didAction.onNext(null)
            }

        })
    }

    var apiEndpoint = "https://pgorelease.nianticlabs.com/plfe/rpc"
    var authTicket: AuthTicketOuterClass.AuthTicket? = null

    private var requestId: Long = 0
    private val random = Random()

    private fun sendRequests(requests: List<Pair<ServerRequest, ReplaySubject<ServerRequest>>>) {
        val envelope = RequestEnvelopeOuterClass.RequestEnvelope.newBuilder()
        envelope.setAltitude(poGoApi.altitude).setLatitude(poGoApi.latitude).setLongitude(poGoApi.longitude)
        // TODO Set ticket when we have a valid one
        if (authTicket != null && authTicket?.expireTimestampMs ?: 0 > poGoApi.currentTimeMillis() - CredentialProvider.REFRESH_TOKEN_BUFFER_TIME) {
            envelope.authTicket = authTicket
        } else {
            envelope.authInfo = credentialProvider.authInfo
        }
        envelope.addAllRequests(requests.map {
            RequestOuterClass.Request.newBuilder()
                    .setRequestMessage(it.first.build(poGoApi).toByteString())
                    .setRequestType(it.first.getRequestType())
                    .build()
        })
        envelope.setStatusCode(2)

        requestId++
        val rand = random.nextLong() or requestId.ushr(31)

        envelope.requestId = rand shl 32 or requestId;

        Signature.setSignature(poGoApi, envelope)

        val stream = ByteArrayOutputStream()
        val request = envelope.build()
        try {
            request.writeTo(stream)
        } catch (e: IOException) {
            System.err.println("Failed to write request to bytearray ouput stream. This should never happen")
        }


        val body = RequestBody.create(null, stream.toByteArray())
        val httpRequest = okhttp3.Request.Builder().url(apiEndpoint).post(body).build()

        try {
            okHttpClient.newCall(httpRequest).execute().use({ response ->
                if (response.code() != 200) {
                    throw RemoteServerException("Got a unexpected http code : " + response.code())
                }

                val responseEnvelope: ResponseEnvelopeOuterClass.ResponseEnvelope =
                        try {
                            response.body().byteStream().use({ content -> ResponseEnvelopeOuterClass.ResponseEnvelope.parseFrom(content) })
                        } catch (e: Exception) {
                            // retrieved garbage from the server
                            throw RemoteServerException("Received malformed response : " + e)
                        }

                if (responseEnvelope.apiUrl != null && responseEnvelope.apiUrl.length > 0) {
                    apiEndpoint = "https://" + responseEnvelope.apiUrl + "/rpc"
                }

                if (responseEnvelope.hasAuthTicket()) {
                    authTicket = responseEnvelope.authTicket
                }

                if (responseEnvelope.statusCode == 102) {
                    throw LoginFailedException(String.format("Invalid Auth status code recieved, token not refreshed?",
                            responseEnvelope.apiUrl, responseEnvelope.error))
                } else if (responseEnvelope.statusCode == 53) {
                    // 53 means that the api_endpoint was not correctly set, should be at this point, though, so redo the request
                    return sendRequests(requests)
                }

                /**
                 * map each reply to the numeric response,
                 * ie first response = first request and send back to the requests to toBlocking.
                 */
                var count = 0
                if (responseEnvelope.returnsCount != requests.size) {
                    System.err.println("Inconsistent replies received; requested "+ requests.size +" payloads; received "+ responseEnvelope.returnsCount);
                }
                for (payload in responseEnvelope.returnsList) {
                    val serverReq = requests[count]
                    /**
                     * TODO: Probably all other payloads are garbage as well in this case,
                     * so might as well throw an exception and leave this loop  */
                    if (payload != null) {
                        serverReq.first.setResponse(payload)
                        poGoApi.handleResponse(serverReq.first)
                        try {
                            serverReq.second.onNext(serverReq.first)
                            serverReq.second.onCompleted()
                        } catch (e: Exception) {
                            System.err.println("Error in handler")
                            e.printStackTrace()
                        }
                    }
                    count++
                }
            })
        } catch (e: IOException) {
            throw RemoteServerException(e)
        } catch (e: RemoteServerException) {
            // catch it, so the auto-close of resources triggers, but don't wrap it in yet another RemoteServer Exception
            throw e
        }
    }

    fun start() {
        didAction.onNext(null)
    }
}