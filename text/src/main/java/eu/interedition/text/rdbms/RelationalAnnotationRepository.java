package eu.interedition.text.rdbms;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import eu.interedition.text.*;
import eu.interedition.text.mem.SimpleQName;
import eu.interedition.text.util.SQL;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.sql.DataSource;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static eu.interedition.text.rdbms.RelationalQNameRepository.mapNameFrom;
import static eu.interedition.text.rdbms.RelationalQNameRepository.selectNameFrom;
import static eu.interedition.text.rdbms.RelationalTextRepository.mapTextFrom;
import static eu.interedition.text.rdbms.RelationalTextRepository.selectTextFrom;
import static eu.interedition.text.util.SimpleAnnotationPredicate.isAny;
import static eu.interedition.text.util.SimpleAnnotationPredicate.isNullOrEmpty;

public class RelationalAnnotationRepository implements AnnotationRepository {

  private SimpleJdbcTemplate jt;
  private RelationalQNameRepository nameRepository;
  private RelationalTextRepository textRepository;
  private SimpleJdbcInsert annotationInsert;
  private SimpleJdbcInsert annotationSetInsert;
  private SimpleJdbcInsert annotationLinkTargetInsert;
  private SAXParserFactory saxParserFactory;

  public void setDataSource(DataSource dataSource) {
    this.jt = (dataSource == null ? null : new SimpleJdbcTemplate(dataSource));
    this.annotationInsert = (jt == null ? null : new SimpleJdbcInsert(dataSource).withTableName("text_annotation").usingGeneratedKeyColumns("id"));
    this.annotationSetInsert = (jt == null ? null : new SimpleJdbcInsert(dataSource).withTableName("text_annotation_link").usingGeneratedKeyColumns("id"));
    this.annotationLinkTargetInsert = (jt == null ? null : new SimpleJdbcInsert(dataSource).withTableName("text_annotation_link_target"));
  }

  public void setNameRepository(RelationalQNameRepository nameRepository) {
    this.nameRepository = nameRepository;
  }

  public void setTextRepository(RelationalTextRepository textRepository) {
    this.textRepository = textRepository;
  }

  public Annotation create(Text text, QName name, Range range) {
    final RelationalAnnotation created = new RelationalAnnotation();
    created.setText((RelationalText) text);
    created.setName(nameRepository.get(name));
    created.setRange(range == null ? Range.NULL : range);

    final HashMap<String, Object> annotationData = Maps.newHashMap();
    annotationData.put("text", ((RelationalText) text).getId());
    annotationData.put("name", ((RelationalQName) nameRepository.get(name)).getId());
    annotationData.put("range_start", range.getStart());
    annotationData.put("range_end", range.getEnd());
    created.setId(annotationInsert.executeAndReturnKey(annotationData).intValue());

    return created;
  }

  public AnnotationLink createLink(QName name) {
    RelationalQName relationalQName = (RelationalQName) nameRepository.get(name);

    final Map<String, Object> setData = Maps.newHashMap();
    setData.put("name", relationalQName.getId());

    return new RelationalAnnotationLink(annotationSetInsert.executeAndReturnKey(setData).intValue(), relationalQName);
  }

  public void delete(AnnotationPredicate predicate) {
    final ArrayList<Object> parameters = new ArrayList<Object>();

    final StringBuilder sql = new StringBuilder("delete from text_annotation where id in (");
    sql.append(buildAnnotationFinderSQL("a.id", predicate, parameters));
    sql.append(")");

    jt.update(sql.toString(), parameters.toArray(new Object[parameters.size()]));
  }

  public void delete(AnnotationLink annotationLink) {
    jt.update("delete from text_annotation_link where id = ?", ((RelationalAnnotationLink) annotationLink).getId());
  }

  public void add(AnnotationLink to, Set<Annotation> toAdd) {
    if (toAdd == null || toAdd.isEmpty()) {
      return;
    }
    final int setId = ((RelationalAnnotationLink) to).getId();
    final List<Map<String, Object>> psList = new ArrayList<Map<String, Object>>(toAdd.size());
    for (RelationalAnnotation annotation : Iterables.filter(toAdd, RelationalAnnotation.class)) {
      final Map<String, Object> ps = new HashMap<String, Object>(2);
      ps.put("link", setId);
      ps.put("target", annotation.getId());
      psList.add(ps);
    }
    annotationLinkTargetInsert.executeBatch(psList.toArray(new Map[psList.size()]));
  }

  public void remove(AnnotationLink from, Set<Annotation> toRemove) {
    if (toRemove == null || toRemove.isEmpty()) {
      return;
    }

    final StringBuilder sql = new StringBuilder("delete from text_annotation_link_target where link = ? and ");

    final List<Object> params = new ArrayList<Object>(toRemove.size() + 1);
    params.add(((RelationalAnnotationLink) from).getId());

    final Set<RelationalAnnotation> annotations = Sets.newHashSet(Iterables.filter(toRemove, RelationalAnnotation.class));
    sql.append("target in (");
    for (Iterator<RelationalAnnotation> it = annotations.iterator(); it.hasNext(); ) {
      params.add(it.next().getId());
      sql.append("?").append(it.hasNext() ? ", " : "");
    }
    sql.append(")");

    jt.update(sql.toString(), params.toArray(new Object[params.size()]));
  }

  @SuppressWarnings("unchecked")
  public Iterable<Annotation> find(AnnotationPredicate predicate) {
    List<Object> parameters = Lists.newArrayList();


    final StringBuilder sql = buildAnnotationFinderSQL(new StringBuilder().
            append(selectAnnotationFrom("a")).append(", ").
            append(selectNameFrom("n")).append(", ").
            append(selectTextFrom("t")).toString(), predicate, parameters);
    sql.append(" order by n.id");

    return Sets.newTreeSet(jt.query(sql.toString(), new RowMapper<Annotation>() {
      private RelationalQName currentName;

      public Annotation mapRow(ResultSet rs, int rowNum) throws SQLException {
        if (currentName == null || currentName.getId() != rs.getInt("n_id")) {
          currentName = mapNameFrom(rs, "n");
        }
        return mapAnnotationFrom(rs, mapTextFrom(rs, "t"), currentName, "a");
      }
    }, parameters.toArray(new Object[parameters.size()])));
  }

  private StringBuilder buildAnnotationFinderSQL(String selectClause, AnnotationPredicate predicate, List<Object> parameters) {
    final StringBuilder sql = new StringBuilder("select ");
    sql.append(selectClause);
    sql.append(" from text_annotation a");
    sql.append(" join text_qname n on a.name = n.id");
    sql.append(" join text_content t on a.text = t.id");

    if (!isAny(predicate)) {
      sql.append(" where 1=1");
    }

    if (!isNullOrEmpty(predicate.annotationEquals())) {
      sql.append(" and a.id in (");
      for (Iterator<Annotation> it = predicate.annotationEquals().iterator(); it.hasNext(); ) {
        parameters.add(((RelationalAnnotation) it.next()).getId());
        sql.append("?").append(it.hasNext() ? ", " : "");
      }
      sql.append(")");
    }

    if (!isNullOrEmpty(predicate.annotationAnnotates())) {
      sql.append(" and a.text in (");
      for (Iterator<Text> it = predicate.annotationAnnotates().iterator(); it.hasNext(); ) {
        parameters.add(((RelationalText) it.next()).getId());
        sql.append("?").append(it.hasNext() ? ", " : "");
      }
      sql.append(")");
    }

    if (!isNullOrEmpty(predicate.annotationhasName())) {
      sql.append(" and a.name in (");
      for (Iterator<QName> it = nameRepository.get(predicate.annotationhasName()).iterator(); it.hasNext(); ) {
        parameters.add(((RelationalQName) it.next()).getId());
        sql.append("?").append(it.hasNext() ? ", " : "");
      }
      sql.append(")");
    }

    if (!isNullOrEmpty(predicate.annotationOverlaps())) {
      sql.append(" and (");
      for (Iterator<Range> it = predicate.annotationOverlaps().iterator(); it.hasNext(); ) {
        final Range range = it.next();
        sql.append("a.range_start < ? and a.range_end > ?");
        sql.append(it.hasNext() ? " or " : "");
        parameters.add(range.getEnd());
        parameters.add(range.getStart());
      }
      sql.append(")");
    }

    return sql;
  }

  public Map<AnnotationLink, Set<Annotation>> findLinks(Set<Text> texts, Set<QName> setNames, Set<QName> names, Map<Text, Set<Range>> ranges) {
    final List<Object> params = new ArrayList<Object>();
    final StringBuilder sql = new StringBuilder("select ");
    sql.append("al.id as al_id, ");
    sql.append(selectAnnotationFrom("a")).append(", ");
    sql.append(selectTextFrom("t")).append(", ");
    sql.append(selectNameFrom("aln")).append(", ");
    sql.append(selectNameFrom("an"));
    sql.append(" from text_annotation_link_target alt");
    sql.append(" join text_annotation_link al on alt.link = al.id");
    sql.append(" join text_qname aln on al.name = aln.id");
    sql.append(" join text_annotation a on alt.target = a.id");
    sql.append(" join text_qname an on a.name = an.id");
    sql.append(" join text_content t on a.text = t.id");

    final boolean textCriteria = texts != null && !texts.isEmpty();
    final boolean setNameCriteria = setNames != null && !setNames.isEmpty();
    final boolean nameCriteria = names != null && !names.isEmpty();
    final boolean rangeCritera = ranges != null && !ranges.isEmpty();
    boolean firstCriteria = true;

    if (textCriteria || setNameCriteria || nameCriteria || rangeCritera) {
      sql.append(" where");
    }
    if (textCriteria) {
      sql.append(" a.text in (");
      for (Iterator<Text> it = texts.iterator(); it.hasNext(); ) {
        params.add(((RelationalText) it.next()).getId());
        sql.append("?").append(it.hasNext() ? ", " : "");
      }
      sql.append(")");
      firstCriteria = false;
    }

    if (setNameCriteria) {
      setNames = nameRepository.get(setNames);
      if (!firstCriteria) {
        sql.append(" and");
      }
      sql.append(" alt.name in (");
      for (Iterator<QName> it = setNames.iterator(); it.hasNext(); ) {
        params.add(((RelationalQName) it.next()).getId());
        sql.append("?").append(it.hasNext() ? ", " : "");
      }
      sql.append(")");
      firstCriteria = false;
    }

    if (nameCriteria) {
      names = nameRepository.get(names);
      if (!firstCriteria) {
        sql.append(" and");
      }
      sql.append(" a.name in (");
      for (Iterator<QName> it = names.iterator(); it.hasNext(); ) {
        params.add(((RelationalQName) it.next()).getId());
        sql.append("?").append(it.hasNext() ? ", " : "");
      }
      sql.append(")");
      firstCriteria = false;
    }

    if (rangeCritera) {
      if (!firstCriteria) {
        sql.append(" and");
      }

      sql.append("(");
      for (Iterator<Map.Entry<Text, Set<Range>>> it = ranges.entrySet().iterator(); it.hasNext(); ) {
        final Map.Entry<Text, Set<Range>> textRangesEntry = it.next();
        final int textId = ((RelationalText) textRangesEntry.getKey()).getId();
        final Set<Range> textRanges = textRangesEntry.getValue();

        for (Iterator<Range> rangeIt = textRanges.iterator(); it.hasNext(); ) {
          final Range range = rangeIt.next();

          params.add(textId);
          params.add(range.getEnd());
          params.add(range.getStart());

          sql.append(" (a.text = ? and a.range_start < ? and a.range_end > ?)");
          sql.append(it.hasNext() ? " or" : "");
        }

        sql.append(it.hasNext() ? " or" : "");
      }
      sql.append(")");
    }

    sql.append(" order by al.id, t.id, an.id, a.id");

    final Map<AnnotationLink, Set<Annotation>> annotationLinks = new HashMap<AnnotationLink, Set<Annotation>>();

    jt.query(sql.toString(), new RowMapper<Void>() {
      private RelationalAnnotationLink currentLink;
      private RelationalText currentText;
      private RelationalQName currentAnnotationName;

      public Void mapRow(ResultSet rs, int rowNum) throws SQLException {
        final int annotationLinkId = rs.getInt("al_id");
        final int textId = rs.getInt("t_id");
        final int annotationNameId = rs.getInt("an_id");

        if (currentLink == null || currentLink.getId() != annotationLinkId) {
          currentLink = new RelationalAnnotationLink(annotationLinkId, mapNameFrom(rs, "aln"));
        }
        if (currentText == null || currentText.getId() != textId) {
          currentText = RelationalTextRepository.mapTextFrom(rs, "t");
        }
        if (currentAnnotationName == null || currentAnnotationName.getId() != annotationNameId) {
          currentAnnotationName = mapNameFrom(rs, "an");
        }

        Set<Annotation> members = annotationLinks.get(currentLink);
        if (members == null) {
          annotationLinks.put(currentLink, members = new TreeSet<Annotation>());
        }
        members.add(mapAnnotationFrom(rs, currentText, currentAnnotationName, "a"));

        return null;
      }

    }, params.toArray(new Object[params.size()]));

    return annotationLinks;
  }

  public SortedSet<QName> names(Text text) {
    final StringBuilder namesSql = new StringBuilder("select distinct ");
    namesSql.append(selectNameFrom("n"));
    namesSql.append(" from text_qname n join text_annotation a on a.name = n.id where a.text = ?");
    final SortedSet<QName> names = Sets.newTreeSet(jt.query(namesSql.toString(), new RowMapper<QName>() {

      public QName mapRow(ResultSet rs, int rowNum) throws SQLException {
        return mapNameFrom(rs, "n");
      }
    }, ((RelationalText) text).getId()));

    if (names.isEmpty() && text.getType() == Text.Type.XML) {
      try {
        textRepository.read(text, new TextRepository.TextReader() {
          public void read(Reader content, int contentLength) throws IOException {
            if (contentLength == 0) {
              return;
            }
            try {
              saxParserFactory().newSAXParser().parse(new InputSource(content), new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                  names.add(new SimpleQName(uri, localName));
                }
              });
            } catch (SAXException e) {
              throw Throwables.propagate(e);
            } catch (ParserConfigurationException e) {
              throw Throwables.propagate(e);
            }
          }
        });
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
    }

    return names;
  }

  public static String selectAnnotationFrom(String tableName) {
    return SQL.select(tableName, "id", "range_start", "range_end");
  }


  public static RelationalAnnotation mapAnnotationFrom(ResultSet rs, RelationalText text, RelationalQName name, String tableName) throws SQLException {
    final RelationalAnnotation relationalAnnotation = new RelationalAnnotation();
    relationalAnnotation.setId(rs.getInt(tableName + "_id"));
    relationalAnnotation.setName(name);
    relationalAnnotation.setText(text);
    relationalAnnotation.setRange(new Range(rs.getInt(tableName + "_range_start"), rs.getInt(tableName + "_range_end")));
    return relationalAnnotation;
  }

  protected SAXParserFactory saxParserFactory() {
    if (saxParserFactory == null) {
      saxParserFactory = SAXParserFactory.newInstance();
      saxParserFactory.setNamespaceAware(true);
      saxParserFactory.setValidating(false);
    }
    return saxParserFactory;
  }
}
