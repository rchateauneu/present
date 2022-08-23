package paquetage;

import java.util.ArrayList;

/**
 * This models an object and its properties extracted from a Sparql query.
 * The work "object" is misleading : It does not mean the right side of an RDF triple.
 * Rather, in this context, an object is a subject variable of a triple which is given a rdf:type.
 * Its predicates and properties are all key-values (key is the predicate) parsed from the query.
 * The term "Instance" might be more appropriate.
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
        Members = new ArrayList<PredicateObjectPair>();
    }

    ObjectPattern(String variableName) {
        this(variableName, null);
    }

    // TODO: ObjectName must be a Variable or Value. See rdf4j
    public record PredicateObjectPair(String Predicate, boolean isVariable, String Content) {}

    public ArrayList<PredicateObjectPair> Members;

    public void AddKeyValue(String predicate, boolean isVariable, String content)
    {
        PredicateObjectPair keyValue = new PredicateObjectPair(predicate, isVariable, content);
        Members.add(keyValue);
    }

    public String toString() {
        return "className=" + className + " VariableName=" + VariableName;
    }
}
