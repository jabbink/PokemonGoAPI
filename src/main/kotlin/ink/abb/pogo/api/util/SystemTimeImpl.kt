package ink.abb.pogo.api.util

class SystemTimeImpl : Time {
    override fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}
