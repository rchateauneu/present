package paquetage;

import org.junit.Assert;
import org.junit.Test;

public class SparqlToWmiTest {
    @Test
    public void FirstTestTest() throws Exception {
        ObjectPattern objectPattern = new ObjectPattern("my_process");

        SparqlBGPExtractor extractor = new SparqlBGPExtractor("""
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            select ?handle
            where {
                ?process rdf:type cim:Win32_Process .
                ?process cim:Handle ?handle .
                }
            """);
        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        Assert.assertEquals(1, 1);
    }
}
