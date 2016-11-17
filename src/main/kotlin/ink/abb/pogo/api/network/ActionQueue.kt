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
import ink.abb.pogo.api.request.CheckChallenge
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

class ActionQueue(val poGoApi: PoGoApi, val okHttpClient: OkHttpClient, val credentialProvider: CredentialProvider) {
    val signature = Signature(poGoApi)

    val maxItems = 10

    val queueInterval = 300L

    var lastRequest = poGoApi.currentTimeMillis()

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

                val newQueue = ConcurrentLinkedDeque<Pair<ServerRequest, ReplaySubject<ServerRequest>>>()
                while (requestQueue.isNotEmpty() && taken < maxItems) {
                    val next = requestQueue.pop()
                    val type = next.first.getRequestType()
                    val lastUsed = lastUseds.getOrElse(type, { 0 })
                    val rateLimit = rateLimits.getOrElse(type, { 0 })
                    if (curTime > lastUsed + rateLimit) {
                        lastUseds.put(type, curTime)
                        queue.add(next)
                        taken++
                    } else {
                        //println("Skipping $type, because $curTime > $lastUsed + $rateLimit")
                        newQueue.offer(next)
                    }
                }
                while (newQueue.isNotEmpty()) {
                    requestQueue.offer(newQueue.pop())
                }

                if (queue.isNotEmpty()) {
                    sendRequests(queue.map { it })
                }

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
        envelope.setAccuracy(Math.random() * 6.0 + 4.0).setLatitude(poGoApi.latitude).setLongitude(poGoApi.longitude)
        // TODO Set ticket when we have a valid one
        val authTicketExpiry: Long = authTicket?.expireTimestampMs ?: 0
        val now = poGoApi.currentTimeMillis()
        if (authTicketExpiry > now + CredentialProvider.REFRESH_TOKEN_BUFFER_TIME) {
            envelope.authTicket = authTicket
            //println("Using authTicket: ${authTicket?.expireTimestampMs} > ${poGoApi.currentTimeMillis() + CredentialProvider.REFRESH_TOKEN_BUFFER_TIME}")
        } else {
            envelope.authInfo = credentialProvider.authInfo
            //println("Using authInfo because ${authTicket?.expireTimestampMs} < ${poGoApi.currentTimeMillis() + CredentialProvider.REFRESH_TOKEN_BUFFER_TIME}")
        }
        envelope.addAllRequests(requests.map {
            RequestOuterClass.Request.newBuilder()
                    .setRequestMessage(it.first.build(poGoApi).toByteString())
                    .setRequestType(it.first.getRequestType())
                    .build()
        })
        val challenge = CheckChallenge().withDebugRequest(false)
        envelope.addRequests(RequestOuterClass.Request.newBuilder()
                .setRequestMessage(challenge.build(poGoApi).toByteString())
                .setRequestType(challenge.getRequestType())
                .build())
        envelope.statusCode = 2

        requestId++
        val rand = random.nextLong() or requestId.ushr(31)

        envelope.requestId = rand shl 32 or requestId;

        signature.setSignature(envelope, lastRequest)
        //println("verification: ${signature.verifySignature(envelope, lastRequest)}")

        val stream = ByteArrayOutputStream()
        val request = envelope.build()
        try {
            request.writeTo(stream)
        } catch (e: IOException) {
            System.err.println("Failed to write request to bytearray ouput stream. This should never happen")
        }

        lastRequest = poGoApi.currentTimeMillis()

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

                if (responseEnvelope.statusCode == ResponseEnvelopeOuterClass.ResponseEnvelope.StatusCode.INVALID_AUTH_TOKEN) {
                    throw LoginFailedException(String.format("Invalid Auth status code received, token not refreshed?",
                            responseEnvelope.apiUrl, responseEnvelope.error))
                } else if (responseEnvelope.statusCode == ResponseEnvelopeOuterClass.ResponseEnvelope.StatusCode.REDIRECT) {
                    // 53 means that the api_endpoint was not correctly set, should be at this point, though, so redo the request
                    Thread.sleep(queueInterval)
                    return sendRequests(requests)
                } else if (responseEnvelope.statusCode != ResponseEnvelopeOuterClass.ResponseEnvelope.StatusCode.OK && responseEnvelope.statusCode != ResponseEnvelopeOuterClass.ResponseEnvelope.StatusCode.OK_RPC_URL_IN_RESPONSE) {
                    System.err.println("Unexpected response envelope status received: ${responseEnvelope.statusCode}; Responses are probably malformed or unreliable")
                }

                /**
                 * map each reply to the numeric response,
                 * ie first response = first request and send back to the requests to toBlocking.
                 */
                var count = 0
                if (responseEnvelope.returnsCount != requests.size + 1) {
                    if (responseEnvelope.returnsCount == requests.size) {
                        //System.err.println("Captcha request - ignoring")
                    } else {
                        System.err.println("Inconsistent replies received; expected " + (requests.size + 1) + " payloads; received " + responseEnvelope.returnsCount);
                        System.err.println("Probable culprit:")
                        System.err.println(requests[responseEnvelope.returnsCount].first.getRequestType())
                        System.err.println(requests[responseEnvelope.returnsCount].first.build(poGoApi).toString())
                        System.err.println("Envelope location: "+ envelope.latitude +" "+ envelope.longitude)
                        requests.drop(responseEnvelope.returnsCount).forEach {
                            val original = it.second
                            // no idea if .subscribe(original) would work here automatically as well
                            System.err.println("Re-queueing ${it.first.getRequestType()}, ${it.first.build(poGoApi).toString()}")
                            poGoApi.queueRequest(it.first).subscribe(
                                    { original.onNext(it) }, { original.onError(it) }, { original.onCompleted() })
                        }
                    }
                }

                for (payload in responseEnvelope.returnsList) {
                    if (count < requests.size) {
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