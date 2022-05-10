package paquetage;

import java.util.ArrayList;

public class ObjectPattern {
    public String VariableName;

    ObjectPattern(String variableName) {
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
