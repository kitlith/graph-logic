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

class Wire: CollapsableNode {
    override fun collapse(graph: MutableNetwork<LogicNode, LogicEdge>) {
        val newEdges: List<NewEdge> = graph.inEdges(this).flatMap({inEdge ->
            val inNode = graph.incidentNodes(inEdge).source()
            val isCollapsable = inNode is CollapsableNode
            graph.outEdges(this)
                .filter({outEdge -> !isCollapsable || inNode != graph.incidentNodes(outEdge).target()})
                .map({outEdge ->
                    NewEdge(inNode, graph.incidentNodes(outEdge).target(), LogicEdge(inEdge.fromChannel, outEdge.toChannel, inEdge.fromUid, outEdge.toUid))
                })
        })

        for (newEdge in newEdges) {
            graph.addEdge(newEdge.from, newEdge.to, newEdge.edge)
        }
        graph.removeNode(this)
    }
}

class BundledWire: CollapsableNode {
    override fun collapse(graph: MutableNetwork<LogicNode, LogicEdge>) {
        val newEdges: List<NewEdge> = if (graph.adjacentNodes(this).filterIsInstance<BundledWire>().any()) {
            // First, we need to merge the BundledWires to gather all the edges together.
            graph.outEdges(this)
                .flatMap({outEdge ->
                    val outNode = graph.incidentNodes(outEdge).target()
                    val outIsBundled = outNode is BundledWire
                    graph.inEdges(this)
                        .filter({inEdge ->
                            val inNode = graph.incidentNodes(inEdge).source()
                            outNode != inNode && (outIsBundled || inNode is BundledWire)
                        }).map({inEdge ->
                            val inNode = graph.incidentNodes(inEdge).source()
                            val inIsBundled = inNode is BundledWire
                            if (outIsBundled && inIsBundled) {
                                NewEdge(inNode, outNode, LogicEdge(inEdge.fromChannel, outEdge.toChannel, inEdge.fromUid, outEdge.toUid))
                            } else if (inIsBundled) {
                                NewEdge(inNode, outNode, outEdge)
                            } else { // outIsBundled
                                NewEdge(inNode, outNode, inEdge)
                            }
                        })
                })
        } else {
            // since all edges are gathered, merge edges by comparing from.toUid and to.fromUid
            graph.inEdges(this).flatMap({inEdge ->
                val inNode = graph.incidentNodes(inEdge).source()
                graph.outEdges(this)
                    .filter({outEdge ->
                        inEdge.toChannel.ch is Channel.Bundled &&
                        outEdge.fromChannel.ch is Channel.Bundled &&
                        inEdge.toChannel.ch == outEdge.fromChannel.ch
                    })
                    .map({outEdge ->
                        NewEdge(inNode, graph.incidentNodes(outEdge).target(), LogicEdge(inEdge.fromChannel, outEdge.toChannel, inEdge.fromUid, outEdge.toUid))
                    })
            })
        }
        graph.removeNode(this)
        for (newEdge in newEdges) {
            graph.addEdge(newEdge.from, newEdge.to, newEdge.edge)
        }
    }
}

interface TilableLogic: CollapsableNode {
    val newNode: LogicNode
    override fun collapse(graph: MutableNetwork<LogicNode, LogicEdge>) {
        val tmpEdge = LogicEdge()
        val newEdges: List<NewEdge> = graph.inEdges(this).flatMap({inEdge ->
            val inNode = graph.incidentNodes(inEdge).source()
            var new = graph.outEdges(this)
                .filter({outEdge ->
                    inEdge.toChannel.ch == outEdge.fromChannel.ch
                })
                .map({outEdge ->
                    NewEdge(inNode, graph.incidentNodes(outEdge).target(), LogicEdge(inEdge, outEdge))
                })
            if (inEdge.toChannel.tag is LogicTag.Default) {
                new += NewEdge(inNode, newNode, LogicEdge(fromChannel=inEdge.fromChannel, fromUid=inEdge.fromUid, toUid=tmpEdge.toUid))
            }
            new
        }) + graph.outEdges(this)
            .filter({outEdge -> outEdge.fromChannel.tag is LogicTag.GateSpecific})
            .map({outEdge ->
                NewEdge(newNode, graph.incidentNodes(outEdge).target(), LogicEdge(toChannel=outEdge.toChannel, fromUid=tmpEdge.fromUid, toUid=outEdge.toUid))
            })

        graph.removeNode(this)
        for (newEdge in newEdges) {
            graph.addEdge(newEdge.from, newEdge.to, newEdge.edge)
        }
    }
}

class DummyLogic: CollapsableNode {
    override fun collapse(graph: MutableNetwork<LogicNode, LogicEdge>) {
        graph.removeNode(this)
    }
}

class NullCell: TilableLogic {
    override val newNode = DummyLogic()
}

class BufferCell: TilableLogic {
    override val newNode = OR()
}

class InvertCell: TilableLogic {
    override val newNode = NOR()
}

/* class AndCell: TilableLogic {
    override val newNode = AND() // unfortunately, this has different semantics.
} */

/* class NullCell: CollapsableNode {
    override fun collapse(graph: MutableNetwork<LogicNode, LogicEdge>) {
        val newEdges: List<NewEdge> = graph.inEdges(this).flatMap({inEdge ->
            graph.inEdges(this).flatMap({inEdge ->
                val inNode = graph.incidentNodes(inEdge).source()
                graph.outEdges(this)
                    .filter({outEdge ->
                        inEdge.toChannel.ch == outEdge.fromChannel.ch
                    })
                    .map({outEdge ->
                        NewEdge(inNode, graph.incidentNodes(outEdge).target(), LogicEdge(inEdge.fromChannel, outEdge.toChannel, inEdge.fromUid, outEdge.toUid))
                    })
            })
        })

        for (newEdge in newEdges) {
            graph.addEdge(newEdge.from, newEdge.to, newEdge.edge)
        }
        graph.removeNode(this)
    }
}

class InvertCell: CollapsableNode {
    override fun collapse(graph: MutableNetwork<LogicNode, LogicEdge>) {
        val newNode = NOR()
        val newEdges: List<NewEdge> = graph.inEdges(this).flatMap({inEdge ->
            graph.inEdges(this).flatMap({inEdge ->
                val inNode = graph.incidentNodes(inEdge).source()
                var new = graph.outEdges(this)
                    .filter({outEdge ->
                        inEdge.toChannel.ch == outEdge.fromChannel.ch
                    })
                    .map({outEdge ->
                        NewEdge(inNode, graph.incidentNodes(outEdge).target(), LogicEdge(inEdge, outEdge))
                    })
                if (inEdge.toChannel.tag is LogicTag.Default) {
                    new += NewEdge(inNode, newNode, LogicEdge(fromChannel=inEdge.fromChannel, fromUid=inEdge.fromUid))
                }
            }) + graph.outEdges(this)
                .filter({outEdge -> outEdge.fromChannel is LogicTag.GateSpecific})
                .map({outEdge ->
                    NewEdge(newNode, graph.incidentNodes(outEdge).target(), LogicEdge(toChannel=outEdge.toChannel, toUid=outEdge.toUid))
                })
        })
    }
} */
