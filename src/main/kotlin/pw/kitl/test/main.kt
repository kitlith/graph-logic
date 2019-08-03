package pw.kitl.test

import com.google.common.graph.*
import org.jgrapht.io.*
import org.jgrapht.graph.guava.MutableNetworkAdapter
import java.io.File

fun main(args : Array<String>) {
    var graph: MutableNetwork<LogicNode, LogicEdge> = NetworkBuilder.directed()
        .allowsParallelEdges(true)
        .allowsSelfLoops(true)
        .build()

    val t = Constant(true)
    val f = Constant(false)
    val xor1 = XOR()
    val xor2 = XOR()
    val xor3 = XOR()
    val w1 = Wire()
    val w2 = Wire()
    val w3 = Wire()
    val bw1 = BundledWire()
    val bw2 = BundledWire()
    val nullcell = NullCell()

    /* graph.addEdge(t, w1, LogicEdge())
    graph.addEdge(f, w1, LogicEdge())
    graph.addEdge(w1, Output("1"), LogicEdge())
    graph.addEdge(w1, Output("2"), LogicEdge()) */

    // nodes are being implicitly added to the graph.
    graph.addEdge(t, xor1, LogicEdge());
    graph.addEdge(t, xor2, LogicEdge());
    graph.addEdge(f, xor1, LogicEdge());
    graph.addEdge(f, xor2, LogicEdge());
    graph.addEdge(xor1, bw1, LogicEdge(toChannel=LogicChannel(ch=Channel.Bundled(0))));
    graph.addEdge(xor2, bw1, LogicEdge(toChannel=LogicChannel(ch=Channel.Bundled(1))));
    graph.addEdge(bw1, nullcell, LogicEdge());
    graph.addEdge(nullcell, bw1, LogicEdge());
    graph.addEdge(nullcell, bw2, LogicEdge());
    graph.addEdge(bw2, nullcell, LogicEdge());
    graph.addEdge(Constant(true), nullcell, LogicEdge(toChannel=LogicChannel(tag=LogicTag.GateSpecific(0))))
    graph.addEdge(nullcell, Output("null_test"), LogicEdge(fromChannel=LogicChannel(tag=LogicTag.GateSpecific(0))))
    graph.addEdge(bw2, xor3, LogicEdge(fromChannel=LogicChannel(ch=Channel.Bundled(0))));
    graph.addEdge(bw2, xor3, LogicEdge(fromChannel=LogicChannel(ch=Channel.Bundled(1))));
    graph.addEdge(xor3, w1, LogicEdge());
    //graph.addEdge(w1, xor3, LogicEdge());
    graph.addEdge(w1, w2, LogicEdge());
    graph.addEdge(w2, w1, LogicEdge());
    graph.addEdge(w2, w3, LogicEdge());
    graph.addEdge(w3, w2, LogicEdge());
    graph.addEdge(w3, Output("main"), LogicEdge());

    val pulseXOR = XOR()
    val pulseOR = OR()
    val latch = LatchCell()
    val latchWire = Wire()
    graph.addEdge(t, pulseXOR, LogicEdge())
    graph.addEdge(t, pulseOR, LogicEdge())
    graph.addEdge(pulseOR, pulseXOR, LogicEdge())
    graph.addEdge(pulseXOR, latch, LogicEdge(toChannel=LogicChannel(tag=LogicTag.GateSpecific(0))))
    graph.addEdge(latch, latchWire, LogicEdge(fromChannel=LogicChannel(tag=LogicTag.GateSpecific(0))))
    graph.addEdge(latchWire, latch, LogicEdge(toChannel=LogicChannel(tag=LogicTag.GateSpecific(0))))
    graph.addEdge(latchWire, Output("latch_en"), LogicEdge())
    graph.addEdge(t, latch, LogicEdge())
    graph.addEdge(latch, Output("latch"), LogicEdge())

    export_graph(graph, "before.dot")

    var collapsed_graph = create_logicgraph();
    collapsed_graph.merge(graph);
    collapse_wires(collapsed_graph)
    export_graph(collapsed_graph, "middle.dot")
    var iterations = 0
    var nodes_to_tick: Set<LogicNode> = setOf(t,f);
    while (nodes_to_tick.size != 0) {
        nodes_to_tick = tick_graph(collapsed_graph, nodes_to_tick)
        iterations += 1
    }
    export_graph(collapsed_graph, "logic.dot")
    println("Took $iterations iterations")

    iterations = 0;
    graph.removeNode(xor1);

    collapsed_graph = create_logicgraph();
    collapsed_graph.merge(graph);
    collapse_wires(collapsed_graph);
    nodes_to_tick = collapsed_graph.nodes(); //graph.successors(bw1); // need to do something more complicated to, say, find differences between the collapsed graphs.
    while (nodes_to_tick.size != 0) {
        nodes_to_tick = tick_graph(collapsed_graph, nodes_to_tick)
        iterations += 1
    }

    export_graph(graph, "after.dot")
    println("Took $iterations iterations")
}

fun export_graph(graph: MutableNetwork<LogicNode, LogicEdge>, filename: String) {
    var adapter = MutableNetworkAdapter(graph)
    val vertexIdProvider = object: ComponentNameProvider<LogicNode> {
        var count = -1
        override fun getName(node: LogicNode): String {
            count += 1;
            return "$count"
        }
    }
    val vertexNameProvider = object: ComponentNameProvider<LogicNode> {
        override fun getName(node: LogicNode): String {
            return node::class.simpleName ?: "Unknown"
        }
    }
    val edgeNameProvider = object: ComponentNameProvider<LogicEdge> {
        override fun getName(edge: LogicEdge): String =
             if (edge.fromChannel.tag != LogicTag.Default) {
                "${edge.fromChannel.tag}:"
            } else {
                ""
            } +  "${edge.value}" + if (edge.toChannel.tag != LogicTag.Default) {
                ":${edge.toChannel.tag}"
            } else {
                ""
            }

    }
    val edgeAttributeProvider = object: ComponentAttributeProvider<LogicEdge> {
        override fun getComponentAttributes(edge: LogicEdge): Map<String, Attribute> {
            return mapOf(
                "samehead" to DefaultAttribute.createAttribute(edge.toUid),
                "sametail" to DefaultAttribute.createAttribute(edge.fromUid)
            )
        }
    }
    val exporter = DOTExporter(vertexIdProvider, vertexNameProvider, edgeNameProvider)

    exporter.exportGraph(adapter, File(filename))
}
