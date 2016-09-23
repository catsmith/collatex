package eu.interedition.collatex.subst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;

import com.google.common.collect.Maps;

import eu.interedition.collatex.simple.SimplePatternTokenizer;

/**
 * Created by ronalddekker on 01/05/16.
 */
public class WitnessNode {
    enum Type {
        text, element
    }

    private WitnessNode parent;
    String data;
    private List<WitnessNode> children;
    private Type type;
    private String sigil;
    Map<String, String> attributes;

    public WitnessNode(String sigil, Type type, String data, Map<String, String> attributes) {
        this.sigil = sigil;
        this.type = type;
        this.data = data;
        this.attributes = attributes;
        this.children = new ArrayList<>();
    }

    public void addChild(WitnessNode child) {
        children.add(child);
        child.parent = this;
    }

    public WitnessNode getLastChild() {
        if (children.isEmpty()) {
            throw new RuntimeException("There are no children!");
        }
        return children.get(children.size() - 1);
    }

    @Override
    public String toString() {
        return data;
    }

    public Stream<WitnessNode> children() {
        return this.children.stream();
    }

    // traverses recursively
    public Stream<WitnessNode> depthFirstNodeStream() {
        return Stream.concat(Stream.of(this), children.stream().map(WitnessNode::depthFirstNodeStream).flatMap(Function.identity()));
    }

    // traverses recursively; only instead of nodes we return events
    public Stream<WitnessNodeEvent> depthFirstNodeEventStream() {
        // NOTE: this if can be removed with inheritance, but that would mean virtual dispatch, so it is not a big win.
        if (this.children.isEmpty()) {
            return Stream.of(new WitnessNodeEvent(this, WitnessNodeEventType.TEXT));
        }

        Stream<WitnessNodeEvent> a = Stream.of(new WitnessNodeEvent(this, WitnessNodeEventType.START));
        Stream<WitnessNodeEvent> b = children.stream().map(WitnessNode::depthFirstNodeEventStream).flatMap(Function.identity());
        Stream<WitnessNodeEvent> c = Stream.of(new WitnessNodeEvent(this, WitnessNodeEventType.END));
        return Stream.concat(a, Stream.concat(b, c));
    }

    public Stream<WitnessNode> parentNodeStream() {
        // note: this implementation is not lazy
        List<WitnessNode> parents = new ArrayList<>();
        WitnessNode current = this.parent;
        while (current != null) {
            parents.add(current);
            current = current.parent;
        }
        return parents.stream();
    }

    // NOTE: this implementation should be faster, but it does not work
    // if (this.parent == null) {
    // return Stream.empty();
    // }
    // Stream a = Stream.of(this.parent);
    // Stream b = Stream.of(this.parent.parentNodeStream());
    // return Stream.concat(a, b);
    // }

    // this class creates a witness tree from a XML serialization of a witness
    // returns the root node of the tree
    public static WitnessNode createTree(String sigil, String witnessXML) {
        // use a stax parser to go from XML data to XML tokens
        WitnessNode initialValue = new WitnessNode(sigil, Type.element, "fake root", null);
        final AtomicReference<WitnessNode> currentNodeRef = new AtomicReference<>(initialValue);
        Function<String, Stream<String>> textTokenizer = SimplePatternTokenizer.BY_WS_OR_PUNCT;
        XMLUtil.getXMLEventStream(witnessXML).forEach(xmlEvent -> {
            if (xmlEvent.isStartElement()) {
                WitnessNode child = WitnessNode.fromStartElement(sigil, xmlEvent.asStartElement());
                currentNodeRef.get().addChild(child);
                currentNodeRef.set(child);

            } else if (xmlEvent.isCharacters()) {
                String text = xmlEvent.asCharacters().getData();
                textTokenizer.apply(text)//
                        .map(s -> new WitnessNode(sigil, Type.text, s, new HashMap<>()))//
                        .collect(Collectors.toList())//
                        .forEach(currentNodeRef.get()::addChild);

            } else if (xmlEvent.isEndElement()) {
                currentNodeRef.set(currentNodeRef.get().parent);
            }
        });

        return currentNodeRef.get().children.get(0);
    }


    private static WitnessNode fromStartElement(String sigil, StartElement startElement) {
        Map<String, String> attributes = Maps.newHashMap();
        startElement.getAttributes().forEachRemaining(a -> {
            Attribute attribute = (Attribute) a;
            attributes.put(attribute.getName().getLocalPart(), attribute.getValue());
        });
        String data2 = startElement.getName().getLocalPart();
        return new WitnessNode(sigil, Type.element, data2, attributes);
    }

    public Type getType() {
        return type;
    }

    public boolean isElement() {
        return Type.element.equals(type);
    }

    public String getSigil() {
        return sigil;
    }

    public enum WitnessNodeEventType {
        START, END, TEXT
    }

    static class WitnessNodeEvent {
        protected WitnessNode node;
        protected WitnessNodeEventType type;

        public WitnessNodeEvent(WitnessNode node, WitnessNodeEventType type) {
            this.node = node;
            this.type = type;
        }

        @Override
        public String toString() {
            return type.toString() + ": " + node.toString();
        }
    }

}
