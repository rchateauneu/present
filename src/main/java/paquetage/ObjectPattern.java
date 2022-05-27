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

    /**
     * This is for debugging purpose. An object can be created with a single call.
     * @param variableName
     * @param the_class
     */
    ObjectPattern(String variableName, String the_class) {
        className = the_class;
        VariableName = variableName;
        Members = new ArrayList<PredicateObject>();
    }

    ObjectPattern(String variableName) {
        this(variableName, null);
    }

    // TODO: ObjectName must be a Variable or Value. See rdf4j
    public record PredicateObject(String Predicate, boolean isVariable, String Content) {}

    public ArrayList<PredicateObject> Members;

    public void AddKeyValue(String predicate, boolean isVariable, String content)
    {
        PredicateObject keyValue = new PredicateObject(predicate, isVariable, content);
        Members.add(keyValue);
    }
}
