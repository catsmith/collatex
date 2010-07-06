package eu.interedition.collatex2.experimental.graph.indexing;

import eu.interedition.collatex2.experimental.graph.IVariantGraphVertex;
import eu.interedition.collatex2.interfaces.INormalizedToken;
import eu.interedition.collatex2.interfaces.IWitnessIndex;

public interface IVariantGraphIndex extends IWitnessIndex {

  IVariantGraphVertex getVertex(INormalizedToken token);
  
}