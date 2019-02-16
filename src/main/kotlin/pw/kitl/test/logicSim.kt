package pw.kitl.test

import com.google.common.graph.MutableNetwork

fun collapse_wires(graph: MutableNetwork<LogicNode, LogicEdge>) {
    for (node in graph.nodes().filterIsInstance<CollapsableNode>()) {
        node.collapse(graph)
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
        node.tickLogic(inputs.values, graph.outEdges(node))
        edges_to_tick.addAll(graph.outEdges(node).filter({edge -> edge.hasUpdated()}))
    }

    edges_to_tick.forEach({edge -> edge.tickEdge()})

    return edges_to_tick.map({edge -> graph.incidentNodes(edge).target()}).toSet()
}
