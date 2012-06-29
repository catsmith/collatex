package eu.interedition.collatex.dekker.matrix;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import eu.interedition.collatex.Token;
import eu.interedition.collatex.graph.VariantGraph;
import eu.interedition.collatex.graph.VariantGraphVertex;
import eu.interedition.collatex.matching.Matches;

public class MatchMatrix implements Iterable<MatchMatrix.Coordinate> {
  static Logger LOG = LoggerFactory.getLogger(MatchMatrix.class);
  private final ArrayTable<VariantGraphVertex, Token, MatchMatrixCell> sparseMatrix;

  //  public static MatchMatrix create0(VariantGraph base, Iterable<Token> witness, Comparator<Token> comparator) {
  //    base.rank()/*.adjustRanksForTranspositions()*/;
  //    Iterable<VariantGraphVertex> baseVertices = base.vertices();
  //    Matches matches = Matches.between(baseVertices, witness, comparator);
  //    MatchMatrix arrayTable = new MatchMatrix(baseVertices, witness);
  //    Set<Token> unique = matches.getUnique();
  //    Set<Token> ambiguous = matches.getAmbiguous();
  //    int column = 0;
  //    for (Token t : witness) {
  //      List<VariantGraphVertex> matchingVertices = matches.getAll().get(t);
  //      if (unique.contains(t)) {
  //        int row = matchingVertices.get(0).getRank() - 1;
  //        arrayTable.set(row, column, new MatchMatixCell(true);
  //      } else {
  //        if (ambiguous.contains(t)) {
  //          for (VariantGraphVertex vgv : matchingVertices) {
  //            int row = vgv.getRank() - 1;
  //            arrayTable.set(row, column, true);
  //          }
  //        }
  //      }
  //      column++;
  //    }
  //    return arrayTable;
  //  }

  public static MatchMatrix create(VariantGraph base, Iterable<Token> witness, Comparator<Token> comparator) {
    base.rank()/*.adjustRanksForTranspositions()*/;
    Map<VariantGraphVertex, Integer> vertexIndex = Maps.newLinkedHashMap();
    Iterable<VariantGraphVertex> baseVertices = base.vertices();
    int index = 0;
    for (VariantGraphVertex baseVertex : baseVertices) {
      vertexIndex.put(baseVertex, index++);
    }
    Matches matches = Matches.between(baseVertices, witness, comparator);
    MatchMatrix arrayTable = new MatchMatrix(baseVertices, witness);
    Set<Token> unique = matches.getUnique();
    Set<Token> ambiguous = matches.getAmbiguous();
    int column = 0;
    for (Token t : witness) {
      List<VariantGraphVertex> matchingVertices = matches.getAll().get(t);
      if (unique.contains(t)) {
        VariantGraphVertex v = matchingVertices.get(0);
        int row = vertexIndex.get(v) - 1;
        int rank = v.getRank();
        arrayTable.set(row, column, new MatchMatrixCell(rank, true));
      } else {
        if (ambiguous.contains(t)) {
          for (VariantGraphVertex vgv : matchingVertices) {
            int row = vertexIndex.get(vgv) - 1;
            arrayTable.set(row, column, new MatchMatrixCell(vgv.getRank(), true));
          }
        }
      }
      column++;
    }
    return arrayTable;
  }

  public MatchMatrix(Iterable<VariantGraphVertex> vertices, Iterable<Token> witness) {
    sparseMatrix = ArrayTable.create(vertices, witness);
  }

  public Boolean at(int row, int column) {
    return Objects.firstNonNull(sparseMatrix.at(row, column).isOn(), false);
  }

  public void set(int row, int column, MatchMatrixCell cell) {
    sparseMatrix.set(row, column, cell);
  }

  public String toHtml() {
    StringBuilder result = new StringBuilder("<table>\n<tr><td></td>\n");
    ArrayList<String> colLabels = columnLabels();
    for (String cLabel : colLabels) {
      result.append("<td>").append(cLabel).append("</td>");
    }
    result.append("</tr>\n");
    int colNum = sparseMatrix.columnKeyList().size();
    ArrayList<String> rLabels = rowLabels();
    int row = 0;
    for (String label : rLabels) {
      result.append("<tr><td>").append(label).append("</td>");
      for (int col = 0; col < colNum; col++)
        if (at(row, col))
          result.append("<td BGCOLOR=\"lightgreen\">M</td>");
        else
          result.append("<td></td>");
      result.append("</tr>\n");
      row++;
    }
    result.append("</table>");
    return result.toString();
  }

  public String toHtml(Archipelago arch) {
    int mat[] = new int[rowNum()];
    for (Island isl : arch.iterator()) {
      for (Coordinate c : isl) {
        mat[c.row] = c.column;
      }
    }
    StringBuilder result = new StringBuilder("<table>\n<tr><td></td>\n");
    ArrayList<String> colLabels = columnLabels();
    for (String cLabel : colLabels) {
      result.append("<td>").append(cLabel).append("</td>");
    }
    result.append("</tr>\n");
    ArrayList<String> rLabels = rowLabels();
    int row = 0;
    for (String label : rLabels) {
      result.append("<tr><td>").append(label).append("</td>");
      if (mat[row] > 0) {
        result.append("<td colspan=\"").append(mat[row]).append("\"></td>").append("<td BGCOLOR=\"lightgreen\">M</td>");
      }
      result.append("</tr>\n");
      row++;
    }
    result.append("</table>");
    return result.toString();
  }

  public ArrayList<String> rowLabels() {
    ArrayList<String> labels = new ArrayList<String>();
    for (VariantGraphVertex vgv : sparseMatrix.rowKeyList()) {
      String token = vgv.toString();
      int pos = token.indexOf(":'");
      if (pos > -1) {
        labels.add(token.substring(pos + 2, token.length() - 2));
      }
    }
    return labels;
  }

  public List<VariantGraphVertex> rowVertices() {
    List<VariantGraphVertex> vertices = Lists.newArrayList();
    for (VariantGraphVertex vgv : sparseMatrix.rowKeyList()) {
      if (vgv.toString().contains(":")) {
        vertices.add(vgv);
      }
    }
    return vertices;
  }

  public ArrayList<String> columnLabels() {
    ArrayList<String> labels = new ArrayList<String>();
    for (Token t : sparseMatrix.columnKeyList()) {
      String token = t.toString();
      int pos = token.indexOf(":'");
      if (pos > -1) {
        //        LOG.info("token={{}}, pos={}", token, pos);
        labels.add(token.substring(pos + 2, token.length() - 1));
      }
    }
    return labels;
  }

  public List<Token> columnTokens() {
    List<Token> tokens = Lists.newArrayList();
    for (Token t : sparseMatrix.columnKeyList()) {
      if (t.toString().contains(":")) {
        tokens.add(t);
      }
    }
    return tokens;
  }

  public ArrayList<Coordinate> allMatches() {
    LOG.info("MatchMatrix.allMatches() called");
    ArrayList<Coordinate> pairs = new ArrayList<Coordinate>();
    int rows = rowNum();
    int cols = colNum();
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        if (at(i, j)) pairs.add(new Coordinate(i, j, 0));
      }
    }
    return pairs;
  }

  public int rowNum() {
    return rowLabels().size();
  }

  public int colNum() {
    return columnLabels().size();
  }

  public ArrayList<Island> getIslands() {
    ArrayList<Island> islands = new ArrayList<Island>();
    ArrayList<Coordinate> allTrue = allMatches();
    for (Coordinate c : allTrue) {
      //			System.out.println("next coordinate: "+c);
      boolean found = false;
      while (!found) {
        for (Island alc : islands) {
          //					System.out.println("inspect island");
          if (alc.neighbour(c)) {
            alc.add(c);
            found = true;
          }
          if (found) break;
        }
        if (!found) {
          //					System.out.println("new island");
          Island island = new Island();
          island.add(c);
          islands.add(island);
        }
        found = true;
      }
    }
    return islands;
  }

  @Override
  public Iterator<Coordinate> iterator() {
    LOG.info("MatchMatrix.iterator() called");
    return new AbstractIterator<Coordinate>() {
      private int row = 0;
      private int col = 0;
      private final int rows = rowNum();
      private final int cols = colNum();

      @Override
      protected Coordinate computeNext() {
        while (row < rows) {
          if (col < cols) {
            return new Coordinate(row, col++, 0);
          }
          row++;
        }
        return endOfData();
      }
    };
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    ArrayList<String> colLabels = columnLabels();
    for (String cLabel : colLabels) {
      result.append(" ").append(cLabel);
    }
    result.append("\n");
    int colNum = sparseMatrix.columnKeyList().size();
    ArrayList<String> rLabels = rowLabels();
    int row = 0;
    for (String label : rLabels) {
      result.append(label);
      for (int col = 0; col < colNum; col++)
        result.append(" ").append(at(row++, col));
      result.append("\n");
    }
    return result.toString();
  }

  public static class Coordinate implements Comparable<Coordinate> {
    int row;
    int column;
    int rank;

    public Coordinate(int row, int column, int rank) {
      this.column = column;
      this.row = row;
      this.rank = rank;
    }

    Coordinate(Coordinate other) {
      this(other.row, other.column, other.rank);
    }

    public int getRow() {
      return row;
    }

    public int getColumn() {
      return column;
    }

    public int getRank() {
      return rank;
    }

    public boolean sameColumn(Coordinate c) {
      return c.column == column;
    }

    public boolean sameRow(Coordinate c) {
      return c.row == row;
    }

    public boolean sameRank(Coordinate c) {
      return c.rank == rank;
    }

    public boolean bordersOn(Coordinate c) {
      return (Math.abs(this.rank - c.getRank()) == 1) && (Math.abs(this.row - c.getRow()) == 1);
    }

    @Override
    public boolean equals(Object o) {
      if (o != null & o instanceof Coordinate) {
        final Coordinate c = (Coordinate) o;
        return (this.row == c.getRow() && this.column == c.getColumn() && this.rank == c.getRank());
      }
      return super.equals(o);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(row, column, rank);
    }

    @Override
    public int compareTo(Coordinate o) {
      int result = column - o.column;
      if (result == 0) {
        result = row - o.row;
      } else
        return result;
      return result == 0 ? rank - o.rank : result;
    }

    @Override
    public String toString() {
      return "(" + row + "," + column + "," + rank + ")";
    }
  }

  /**
   * A DirectedIsland is a collections of Coordinates all on the same
   * diagonal. The direction of this diagonal can be -1, 0, or 1.
   * The zero is for a DirectedIsland of only one Coordinate.
   * Directions 1 and -1 examples
   * Coordinates (0,0) (1,1) have Direction 1
   * Coordinates (1,1) (2,1) have Direction -1
   * I.e. if the row-coordinate gets larger and the col-coordinate also, the
   * direction is 1 (positive) else it is -1 (negative)
   */
  public static class Island implements Iterable<Coordinate> {

    private int direction = 0;
    private final List<Coordinate> islandCoordinates = Lists.newArrayList();

    public Island() {}

    public Island(Island other) {
      for (Coordinate c : other.islandCoordinates) {
        add(new Coordinate(c));
      }
    }

    public Island(Coordinate first, Coordinate last) {
      add(first);
      Coordinate newCoordinate = first;
      while (!newCoordinate.equals(last)) {
        newCoordinate = new Coordinate(newCoordinate.getRow() + 1, newCoordinate.getColumn() + 1, newCoordinate.getRank() + 1);
        //        LOG.info("{}", newCoordinate);
        add(newCoordinate);
      }
    }

    public boolean add(Coordinate coordinate) {
      boolean result = false;
      if (islandCoordinates.isEmpty()) {
        result = islandCoordinates.add(coordinate);
      } else if (!contains(coordinate) && neighbour(coordinate)) {
        if (direction == 0) {
          Coordinate existing = islandCoordinates.get(0);
          direction = (existing.row - coordinate.row) / (existing.column - coordinate.column);
          result = islandCoordinates.add(coordinate);
        } else {
          Coordinate existing = islandCoordinates.get(0);
          if (existing.column != coordinate.column) {
            int new_direction = (existing.row - coordinate.row) / (existing.column - coordinate.column);
            if (new_direction == direction) result = islandCoordinates.add(coordinate);
          }
        }
      }
      return result;
    }

    public int direction() {
      return direction;
    }

    public Island removePoints(Island di) {
      Island result = new Island(this);
      for (Coordinate c : di) {
        result.removeSameColOrRow(c);
      }
      return result;
    }

    public Coordinate getCoorOnRow(int row) {
      for (Coordinate coor : islandCoordinates) {
        if (coor.getRow() == row) return coor;
      }
      return null;
    }

    public Coordinate getCoorOnCol(int col) {
      for (Coordinate coor : islandCoordinates) {
        if (coor.getColumn() == col) return coor;
      }
      return null;
    }

    public void merge(Island di) {
      for (Coordinate c : di) {
        add(c);
      }
    }

    /**
     * Two islands are competitors if there is a horizontal or
     * vertical line which goes through both islands
     */
    public boolean isCompetitor(Island isl) {
      for (Coordinate c : isl) {
        for (Coordinate d : islandCoordinates) {
          if (c.sameColumn(d) || c.sameRow(d)) return true;
        }
      }
      return false;
    }

    public boolean contains(Coordinate c) {
      return islandCoordinates.contains(c);
    }

    public boolean neighbour(Coordinate c) {
      if (contains(c)) return false;
      for (Coordinate islC : islandCoordinates) {
        if (c.bordersOn(islC)) {
          return true;
        }
      }
      return false;
    }

    public Coordinate getLeftEnd() {
      Coordinate coor = islandCoordinates.get(0);
      for (Coordinate c : islandCoordinates) {
        if (c.column < coor.column) coor = c;
      }
      return coor;
    }

    public Coordinate getRightEnd() {
      Coordinate coor = islandCoordinates.get(0);
      for (Coordinate c : islandCoordinates) {
        if (c.column > coor.column) coor = c;
      }
      return coor;
    }

    public boolean overlap(Island isl) {
      for (Coordinate c : isl) {
        if (contains(c) || neighbour(c)) return true;
      }
      return false;
    }

    public int size() {
      return islandCoordinates.size();
    }

    public void clear() {
      islandCoordinates.clear();
    }

    public int value() {
      final int size = size();
      return (size < 2 ? size : direction + size * size);
    }

    protected boolean removeSameColOrRow(Coordinate c) {
      ArrayList<Coordinate> remove = new ArrayList<Coordinate>();
      for (Coordinate coor : islandCoordinates) {
        if (coor.sameColumn(c) || coor.sameRow(c)) {
          remove.add(coor);
        }
      }
      if (remove.isEmpty()) return false;
      for (Coordinate coor : remove) {
        islandCoordinates.remove(coor);
      }
      return true;
    }

    @Override
    public Iterator<Coordinate> iterator() {
      return Collections.unmodifiableList(islandCoordinates).iterator();
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(islandCoordinates);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) return false;

      if (!obj.getClass().equals(Island.class)) return false;

      Island isl = (Island) obj;
      if (isl.size() != size()) return false;

      boolean result = true;
      for (Coordinate c : isl) {
        result &= this.contains(c);
      }
      return result;
    }

    @Override
    public String toString() {
      return MessageFormat.format("Island ({0}-{1}) size: {2}", islandCoordinates.get(0), islandCoordinates.get(islandCoordinates.size() - 1), size());
      //      return Iterables.toString(islandCoordinates);
    }
  }

  public static class MatchMatrixCell {
    boolean on = false;
    int rank = 0;

    public MatchMatrixCell(int rank, boolean on) {
      this.rank = rank;
      this.on = on;
    }

    public boolean isOn() {
      return on;
    }

    public int getRank() {
      return rank;
    }
  }
}
