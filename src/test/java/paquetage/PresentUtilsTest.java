package paquetage;

import org.junit.Assert;
import org.junit.Test;

public class PresentUtilsTest {

     @Test
     public void test_hasWmiReferenceSyntax_1() throws Exception {
          boolean hasSyntax = PresentUtils.hasWmiReferenceSyntax("Win32_Volume.DeviceID=\"\\\\\\\\?\\\\Volume{e88d2f2b-332b-4eeb-a420-20ba76effc48}\\\\\"");
          Assert.assertEquals(true, hasSyntax);
     }

     @Test
     public void test_hasWmiReferenceSyntax_2() throws Exception {
          boolean hasSyntax = PresentUtils.hasWmiReferenceSyntax("Win32_Directory.Name=\"C:\\\\\"");
          Assert.assertEquals(true, hasSyntax);
     }

     @Test
     public void test_hasWmiReferenceSyntax_3() throws Exception {
          boolean hasSyntax = PresentUtils.hasWmiReferenceSyntax("\\\\LAPTOP-R89KG6V1\\ROOT\\StandardCimv2:MSFT_NetIPAddress.CreationClassName=\"\",Name=\"poB:DD;C:@D<n>nD==:@DB=:m/;@55;@55;55;\",SystemCreationClassName=\"\",SystemName=\"\"");
          Assert.assertEquals(true, hasSyntax);
     }

     @Test
     public void test_hasWmiReferenceSyntax_4() throws Exception {
          boolean hasSyntax = PresentUtils.hasWmiReferenceSyntax("abc");
          Assert.assertEquals(false, hasSyntax);
     }

     @Test
     public void test_hasUrlSyntax_1() throws Exception {
          boolean hasSyntax = PresentUtils.hasUrlSyntax("https://rdf4j.org/");
          Assert.assertEquals(true, hasSyntax);
     }

     @Test
     public void test_hasUrlSyntax_2() throws Exception {
          boolean hasSyntax = PresentUtils.hasUrlSyntax("abc");
          Assert.assertEquals(false, hasSyntax);
     }

     @Test
     public void test_XmlToLong_1() throws Exception {
          long longResult = PresentUtils.xmlToLong("\"41\"^^<http://www.w3.org/2001/XMLSchema#integer>");
          Assert.assertEquals(41, longResult);
     }

     @Test
     public void test_XmlToBoolean_1() throws Exception {
          boolean boolResult = PresentUtils.xmlToBoolean("\"true\"^^<http://www.w3.org/2001/XMLSchema#boolean>");
          Assert.assertEquals(true, boolResult);
     }

     @Test
     public void test_XmlToBoolean_2() throws Exception {
          boolean boolResult = PresentUtils.xmlToBoolean("\"false\"^^<http://www.w3.org/2001/XMLSchema#boolean>");
          Assert.assertEquals(false, boolResult);
     }

     public void test_extractStringXMLConditional_1() throws Exception {
          PresentUtils.ParsedXMLTag parsedXML = new PresentUtils.ParsedXMLTag("\"false\"^^<http://www.w3.org/2001/XMLSchema#boolean>");
          Assert.assertEquals("false", parsedXML.value);
          Assert.assertEquals("http://www.w3.org/2001/XMLSchema#boolean", parsedXML.datatype);
     }

     public void test_extractStringXMLConditional_2() throws Exception {
          PresentUtils.ParsedXMLTag parsedXML = new PresentUtils.ParsedXMLTag("\"2022-07-20\"^^<http://www.w3.org/2001/XMLSchema#date>");
          Assert.assertEquals("2022-07-20", parsedXML.value);
          Assert.assertEquals("http://www.w3.org/2001/XMLSchema#date", parsedXML.datatype);
     }
     public void test_extractStringXMLConditional_3() throws Exception {
          PresentUtils.ParsedXMLTag parsedXML = new PresentUtils.ParsedXMLTag("\"2022-02-11T00:44:44.730519\"^^<http://www.w3.org/2001/XMLSchema#dateTime>");
          Assert.assertEquals("2022-02-11T00:44:44.730519", parsedXML.value);
          Assert.assertEquals("http://www.w3.org/2001/XMLSchema#dateTime", parsedXML.datatype);
     }

     public void test_extractStringXMLConditional_4() throws Exception {
          PresentUtils.ParsedXMLTag parsedXML = new PresentUtils.ParsedXMLTag("any string");
          Assert.assertEquals("any string", parsedXML.value);
          Assert.assertEquals(null, parsedXML.datatype);
     }

     public void test_Internationalize() throws Exception {
          Assert.assertEquals("\"any\"@en", PresentUtils.internationalizeQuoted("any"));
          Assert.assertEquals("\"any\"@en", PresentUtils.internationalizeQuoted("\"any\""));
     }
}

