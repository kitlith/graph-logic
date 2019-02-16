package pw.kitl.test

import java.util.Objects

sealed class Channel {
    object Default: Channel()
    data class Bundled(val ch: Int): Channel()
}

sealed class LogicTag {
    object Default : LogicTag()
    data class GateSpecific(val tag: Int): LogicTag()
}

class LogicChannel(val ch: Channel = Channel.Default, val tag: LogicTag = LogicTag.Default)

class LogicEdge(val fromChannel: LogicChannel = LogicChannel(), val toChannel: LogicChannel = LogicChannel(), val fromUid: Int = currentUid++, val toUid: Int = currentUid++, value: Boolean = false, var queuedValue: Boolean = false) {
    var value: Boolean
        private set

    companion object {
        var currentUid: Int = 0
    }

    init {
        this.value = value
    }

    constructor(from: LogicEdge, to: LogicEdge): this(from.fromChannel, to.toChannel, from.fromUid, to.toUid) {
        // helper constructor for common use in collapsing.
    }

    override operator fun equals(other: Any?) = when (other) {
        is LogicEdge -> fromUid == other.fromUid && toUid == other.toUid
        else -> false
    }

    override fun hashCode() = Objects.hash(fromUid, toUid)

    fun tickEdge() {
        value = queuedValue
    }
    fun hasUpdated(): Boolean = value != queuedValue
}
