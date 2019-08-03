package pw.kitl.test

import com.google.common.graph.MutableNetwork
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance



interface LogicNode {
    fun updateState(inputs: Iterable<Pair<LogicChannel, Boolean>>)
    fun getOutputs(outputs: Iterable<LogicEdge>)
}

interface CombinatorialNode: LogicNode {
    var output: Boolean
    fun getValue(inputs: Iterable<Pair<LogicChannel, Boolean>>): Boolean
    override fun updateState(inputs: Iterable<Pair<LogicChannel, Boolean>>) {
        output = getValue(inputs)
    }
    override fun getOutputs(outputs: Iterable<LogicEdge>) {
        for (each in outputs) {
            each.queuedValue = output
        }
    }
}

open class OR: CombinatorialNode {
    override var output = false
    override fun getValue(inputs: Iterable<Pair<LogicChannel, Boolean>>)
        = inputs.any({item -> item.second})
}
class NOR: OR() {
    override var output = true
    override fun getValue(inputs: Iterable<Pair<LogicChannel, Boolean>>)
        = !super.getValue(inputs)
}
open class XOR: CombinatorialNode {
    override var output = false
    override fun getValue(inputs: Iterable<Pair<LogicChannel, Boolean>>)
        = inputs.fold(false, {a, b -> a xor b.second})
}
class XNOR: XOR() {
    override var output = true
    override fun getValue(inputs: Iterable<Pair<LogicChannel, Boolean>>)
        = !super.getValue(inputs)
}
open class AND: CombinatorialNode {
    override var output = false
    override fun getValue(inputs: Iterable<Pair<LogicChannel, Boolean>>)
        = inputs.all({item -> item.second})
}
class NAND: AND() {
    override var output = true
    override fun getValue(inputs: Iterable<Pair<LogicChannel, Boolean>>)
        = !super.getValue(inputs)
}

class Constant(val value: Boolean): LogicNode {
    override fun updateState(inputs: Iterable<Pair<LogicChannel, Boolean>>) {
        // no inputs.
    }

    override fun getOutputs(outputs: Iterable<LogicEdge>) {
        for (each in outputs) {
            each.queuedValue = value
        }
    }
}

class Output(val name: String): LogicNode {
    var state = false;
    override fun updateState(inputs: Iterable<Pair<LogicChannel, Boolean>>) {
        val oldState = state
        state = inputs.any({value -> value.second})
        if (state != oldState) println("Output $name changed to $state!")
    }
    override fun getOutputs(outputs: Iterable<LogicEdge>) {
        // no outputs
    }
}

class TransparentLatch: LogicNode {
    var state = false;
    override fun updateState(inputs: Iterable<Pair<LogicChannel, Boolean>>) {
        val updateState = inputs.any({entry -> entry.first.tag == LogicTag.GateSpecific(0) && entry.second})
        if (updateState) {
            state = inputs.any({entry -> entry.first.tag == LogicTag.Default && entry.second})
        }
    }
    override fun getOutputs(outputs: Iterable<LogicEdge>) {
        for (each in outputs) {
            each.queuedValue = state
        }
    }
}

data class NewEdge(val from: LogicNode, val to: LogicNode, val edge: LogicEdge)

interface CollapsableNode: LogicNode {
    fun collapse(graph: MutableNetwork<LogicNode, LogicEdge>)

    override fun updateState(inputs: Iterable<Pair<LogicChannel, Boolean>>)
        = TODO("ticking of collapsable objects not supported")

    override fun getOutputs(outputs: Iterable<LogicEdge>)
        = TODO("getting outputs of collapsable objects not supported")
}

open class Wire: CollapsableNode {
    // performs (the equiv of) a bipartite graph projection on the subgraph of this node and its neighbors
    override fun collapse(graph: MutableNetwork<LogicNode, LogicEdge>) {
        val newEdges: List<NewEdge> = graph.outEdges(this)
            .flatMap({outEdge ->
                graph.inEdges(this)
                    .filter({inEdge ->
                        !(inEdge.toChannel.ch is Channel.Bundled && outEdge.fromChannel.ch is Channel.Bundled) ||
                        inEdge.toChannel.ch == outEdge.fromChannel.ch
                    }).map({inEdge ->
                        NewEdge(
                            graph.incidentNodes(inEdge).source(),
                            graph.incidentNodes(outEdge).target(),
                            when {
                                inEdge.toChannel.ch == outEdge.fromChannel.ch -> LogicEdge(
                                    inEdge.fromChannel,
                                    outEdge.toChannel,
                                    inEdge.fromUid,
                                    outEdge.toUid
                                )
                                inEdge.toChannel.ch is Channel.Bundled -> inEdge
                                outEdge.fromChannel.ch is Channel.Bundled -> outEdge
                                else -> TODO("Shouldn't happen!")
                            }
                        )
                    })
            })

        graph.removeNode(this)
        for (newEdge in newEdges) {
            graph.addEdge(newEdge.from, newEdge.to, newEdge.edge)
        }
        graph.removeNode(this) // double remove for workaround for self-loop
    }
}

// seperate class for rendering purposes, completely identical to Wire.
class BundledWire: Wire()

interface TilableLogic: CollapsableNode {
    val extraNode: LogicNode
    val mainNode: LogicNode

    fun additional_considerations(graph: LogicNetwork)

    override fun collapse(graph: LogicNetwork) {
        val newEdges = graph.inEdges(this)
        .filter({edge ->
            val incident = graph.incidentNodes(edge)
            incident.source() != incident.target()
        })
        .map({edge ->
            val src = graph.incidentNodes(edge).source()
            when (edge.toChannel.tag) {
                is LogicTag.Default -> NewEdge(src, mainNode, edge)
                is LogicTag.GateSpecific -> NewEdge(src, extraNode, edge)
            }
        }) +
        graph.outEdges(this)
        .filter({edge ->
            val incident = graph.incidentNodes(edge)
            incident.source() != incident.target()
        })
        .map({edge ->
            val target = graph.incidentNodes(edge).target()
            when (edge.fromChannel.tag) {
                is LogicTag.Default -> NewEdge(mainNode, target, edge)
                is LogicTag.GateSpecific -> NewEdge(extraNode, target, edge)
            }
        });

        graph.removeNode(this)
        graph.addNode(extraNode)
        graph.addNode(mainNode)
        for (newEdge in newEdges) {
            graph.addEdge(newEdge.from, newEdge.to, newEdge.edge)
        }

        this.additional_considerations(graph)

        (extraNode as? CollapsableNode)?.collapse(graph)
        (mainNode as? CollapsableNode)?.collapse(graph)
    }
}

interface BiTilable: TilableLogic {
    override val extraNode: Wire
    override val mainNode: Wire

    val newNode: LogicNode

    override fun additional_considerations(graph: LogicNetwork) {
        graph.addEdge(mainNode, newNode, LogicEdge())
        graph.addEdge(newNode, extraNode, LogicEdge())

        (newNode as? CollapsableNode)?.collapse(graph)
    }
}

interface UniTilable: TilableLogic {
    override val extraNode: Wire

    override fun additional_considerations(graph: LogicNetwork) {
        graph.addEdge(extraNode, mainNode, LogicEdge())
    }
}

class NullCell: TilableLogic {
    override val extraNode = Wire()
    override val mainNode = Wire()

    override fun additional_considerations(graph: LogicNetwork) {}
}

class BufferCell: BiTilable {
    override val extraNode = Wire()
    override val mainNode = Wire()
    override val newNode = OR()
}

class InvertCell: BiTilable {
    override val extraNode = Wire()
    override val mainNode = Wire()
    override val newNode = NOR()
}

class AndCell: UniTilable {
    override val extraNode = Wire()
    override val mainNode = AND()
}

class XorCell: UniTilable {
    override val extraNode = Wire()
    override val mainNode = XOR()
}

class LatchCell: UniTilable {
    override val extraNode = Wire()
    override val mainNode = TransparentLatch()
}
