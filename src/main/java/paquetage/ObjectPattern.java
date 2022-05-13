package paquetage;

import java.util.ArrayList;

/**
 * This models an object and its properties extracted from a Sparql.
 * The work "object" is misleading : It does not mean the right side of a RDF triple.
 * An object is a subject variable of a triple which is given a rdf:type.
 * Its predicates and properties are all key-values (key s the predicate)
 * parsed from the query.
 */
public class ObjectPattern {
    public String className;
    public String VariableName;

    ObjectPattern(String variableName) {
        className = null;
        VariableName = variableName;
        Members = new ArrayList<KeyValue>();
    }

    // TODO: ObjectName must be a Variable or Value. See rdf4j
    public record KeyValue(String Predicate, boolean isVariable, String Content) {}

    public ArrayList<KeyValue> Members;

    public void AddKeyValue(String predicate, boolean isVariable, String content)
    {
        KeyValue keyValue = new KeyValue(predicate, isVariable, content);
        Members.add(keyValue);
    }

}
