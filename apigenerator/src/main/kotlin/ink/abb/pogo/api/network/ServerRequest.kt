package ink.abb.pogo.api.network

import com.google.protobuf.ByteString
import ink.abb.pogo.api.PoGoApi


interface ServerRequest {
    fun build(poGoApi: PoGoApi): com.google.protobuf.GeneratedMessage
    fun getRequestType(): Any

    fun setResponse(payload: ByteString)
}