package pw.kitl.test

import com.google.common.graph.Network
import com.google.common.graph.MutableNetwork
import com.google.common.graph.NetworkBuilder

fun <N,E> MutableNetwork<N,E>.merge(other: Network<N,E>) {
    for (edge in other.edges()) {
        var endpoints = other.incidentNodes(edge)
        this.addEdge(endpoints.source(), endpoints.target(), edge);
    }
}

fun create_logicgraph(): MutableNetwork<LogicNode, LogicEdge>
    = NetworkBuilder.directed()
        .allowsParallelEdges(true)
        .allowsSelfLoops(true)
        .build()

fun collapse_wires(graph: MutableNetwork<LogicNode, LogicEdge>) {
    for (node in graph.nodes().filterIsInstance<CollapsableNode>()) {
        node.collapse(graph)
    }

    for (node in graph.nodes()) {
        node.getOutputs(graph.outEdges(node))
    }
}

fun tick_graph(graph: MutableNetwork<LogicNode, LogicEdge>, nodes_to_tick: Iterable<LogicNode> = graph.nodes()): Set<LogicNode> {
    var edges_to_tick: MutableList<LogicEdge> = mutableListOf()
    for (node in nodes_to_tick) {
        var inputs = HashMap<Int, Pair<LogicChannel,Boolean>>()
        for (edge in graph.inEdges(node)) {
            if (!inputs.getOrPut(edge.toUid, {-> Pair(edge.toChannel, edge.value)}).second) {
                inputs.put(edge.toUid, Pair(edge.toChannel, edge.value))
            }
        }
        node.updateState(inputs.values)
        node.getOutputs(graph.outEdges(node))
        edges_to_tick.addAll(graph.outEdges(node).filter({edge -> edge.hasUpdated()}))
    }

    edges_to_tick.forEach({edge -> edge.tickEdge()})

    return edges_to_tick.map({edge -> graph.incidentNodes(edge).target()}).toSet()
}
