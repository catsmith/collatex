package eu.interedition.collatex.matrixlinker;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;

import eu.interedition.collatex.Token;
import eu.interedition.collatex.dekker.TokenLinker;
import eu.interedition.collatex.graph.VariantGraph;
import eu.interedition.collatex.graph.VariantGraphVertex;
import eu.interedition.collatex.matrixlinker.MatchMatrix.Coordinates;
import eu.interedition.collatex.matrixlinker.MatchMatrix.Island;

public class MatchMatrixLinker implements TokenLinker {

  @Override
  public Map<Token, VariantGraphVertex> link(VariantGraph base, Iterable<Token> witness, Comparator<Token> comparator) {
    MatchMatrix buildMatrix = MatchMatrix.create(base, witness, comparator);
    ArchipelagoWithVersions archipelago = new ArchipelagoWithVersions();
    for (MatchMatrix.Island isl : buildMatrix.getIslands()) {
      archipelago.add(isl);
    }
    Archipelago createFirstVersion = archipelago.createFirstVersion();
    List<Token> columnTokens = buildMatrix.columnTokens();
    List<VariantGraphVertex> rowVertices = buildMatrix.rowVertices();
    Map<Token, VariantGraphVertex> map = Maps.newHashMap();
    for (Island island : createFirstVersion.iterator()) {
      for (Coordinates c : island) {
        map.put(columnTokens.get(c.column), rowVertices.get(c.row));
      }
    }
    return map;
  }

}
