package eu.interedition.collatex2.decision_graph;


import org.junit.Test;


import eu.interedition.collatex2.implementation.CollateXEngine;
import eu.interedition.collatex2.implementation.decision_graph.DGEdge;
import eu.interedition.collatex2.implementation.decision_graph.DGVertex;
import eu.interedition.collatex2.implementation.decision_graph.DecisionGraph;
import eu.interedition.collatex2.implementation.decision_graph.DecisionGraphVisitor;
import eu.interedition.collatex2.interfaces.IVariantGraph;
import eu.interedition.collatex2.interfaces.IWitness;

public class DecisionGraphTest {

  @Test
  public void testDecisionGraphOmission() {
    CollateXEngine engine = new CollateXEngine();
    IWitness a = engine.createWitness("a", "The red cat and the black cat");
    IWitness b = engine.createWitness("b", "the black cat");
    IVariantGraph graph = engine.graph(a);
    
    //  the -> The
    //  the -> the
    //  black -> black
    //  cat -> cat
    //  cat -> cat
    // bij een decision tree zou de black wegvallen
    // we maken er een graaf van, dan krijgen we twee cirkels als het ware
    // shortest path
    // bij elke vertex bijhouden wat de minimum weight daar is
    // bij elke edge bijhouden of hij deel uitmaakt van het shortest path
    // dan zou het mogelijk moeten zijn om meerdere shortest paths 
    // te reconstrueren
    

    // we use a weighted DAG to make alignment decisions
    
    // eerst de vertices
    DecisionGraph dGraph = new DecisionGraph(graph.getStartVertex());
    DGVertex v1 = dGraph.getStartVertex();
    DGVertex vThe1 = new DGVertex(a.getTokens().get(0));
    DGVertex vThe2 = new DGVertex(a.getTokens().get(4));
    DGEdge e1 = new DGEdge(v1, vThe1, 0); // 0 = no gap -> ENumeration?
    DGEdge e2 = new DGEdge(v1, vThe2, 1); // 1 = gap
    dGraph.add(v1, vThe1, vThe2);
    dGraph.add(e1, e2);
    DGVertex vB = new DGVertex(a.getTokens().get(5));
    DGEdge e3 = new DGEdge(vThe1, vB, 1); 
    DGEdge e4 = new DGEdge(vThe2, vB, 0);
    dGraph.add(vB);
    dGraph.add(e3, e4);
    DGVertex vC1 = new DGVertex(a.getTokens().get(2));
    DGVertex vC2 = new DGVertex(a.getTokens().get(6));
    DGEdge e5 = new DGEdge(vB, vC1, 1);
    DGEdge e6 = new DGEdge(vB, vC2, 0);
    dGraph.add(vC1, vC2);
    dGraph.add(e5, e6);
    DGVertex end = dGraph.getEndVertex();
    DGEdge e7 = new DGEdge(vC1, end, 1);
    DGEdge e8 = new DGEdge(vC2, end, 0);
    dGraph.add(end);
    dGraph.add(e7, e8);
    
    DecisionGraphVisitor.determineMinimumNumberOfGaps(dGraph);
    
    // ik kan nu een nieuwe graph maken waarbij ik alle vertices en edges die niet kleiner 
    // of gelijk de minimum weight zijn deleten
    // maar of dat echt nodig is
    // is nog maar de vraag
    
    
    
    // we moeten bijhouden welk pad we gelopen hebben in de vorm van edges
    // ook moeten we bijhouden welke stappen we nog moeten zetten
    // daar twijfel ik tussen de vertices en de edges
    // aangezien je wil recursen bij meerdere vertices..
    // en dan dus een bepaalde edge meegeven om te doen...
    // laten we initialisen met een bepaalde edge
//    DGVertex start = graph.getStartVertex();
//    List<List<DGEdge>> initialpaths = Lists.newArrayList();
//    initialpaths.add(new ArrayList<DGEdge>());
//    List<List<DGEdge>> paths = traverseVertex(initialpaths, graph, vertexToMinWeight, minGaps, start);
//    System.out.println(paths);
//    DGEdge[] bla = new DGEdge[] { new DGEdge(start, start, minGaps), new DGEdge(start, start, minGaps) };

  }
  
  // we kunnen natuurlijk de graph in een tree converten
  // door strategies de vertex te dupliceren
  // dan kun je alle paden vinden door de leaf nodes af te lopen
  // een dag zou meerdere start nodes kunne hebben
  // daar is er geen algoritme voor
  // maar mijn dag heeft maar 1 start node..
  // aargh
  
  
  // het moet wel met een graph want anders wordt het nix
  // in een nromale decision tree schuif je dan die ene optie in de bij de andere
  // dan ben ik echter de kost kwijt
  // of je kunt in dit geval zeggen dat die case niet bestaat
  // maar das niet echt mooi
  
//
//
//
//private List<List<DGEdge>> traverseVertex(List<List<DGEdge>> paths, DecisionGraph graph,
//    Map<DGVertex, Integer> vertexToMinWeight, int minGaps, DGVertex source) {
//  Set<DGEdge> outgoingEdges = graph.outgoingEdgesOf(source);
//  // hier moeten we kijken hoeveel outgoingEdges source heeft
//  // 0 == we zijn klaar; return de paden gewoon zoals ze zijn
//  // 1 == vul het huidige path gewoon aan en we zijn klaar
//  // >1 == maak extra paden aan in de list!
//  // hier het aantal outgoing edges checken werkt niet,
//  // want er kunnen er een aantal onzichtbaar zijn..
//  
//  for (DGEdge edge : outgoingEdges) {
//    DGVertex targetVertex = edge.getTargetVertex();
//    if (vertexToMinWeight.get(targetVertex) <= minGaps) {
//      // we willen dit path bewandelen
//      for (List<DGEdge> path : paths) {
//        path.add(edge);
//      }
//      traverseVertex(paths, graph, vertexToMinWeight, minGaps, targetVertex);
//    }
//  }
//  return paths;
//}

}
