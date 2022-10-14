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
          long longResult = PresentUtils.XmlToLong("\"41\"^^<http://www.w3.org/2001/XMLSchema#integer>");
          Assert.assertEquals(41, longResult);
     }

     public void test_XmlToBoolean_1() throws Exception {
          boolean boolResult = PresentUtils.XmlToBoolean("\"true\"^^<http://www.w3.org/2001/XMLSchema#boolean>");
          Assert.assertEquals(true, boolResult);
     }

     public void test_XmlToBoolean_2() throws Exception {
          boolean boolResult = PresentUtils.XmlToBoolean("\"false\"^^<http://www.w3.org/2001/XMLSchema#boolean>");
          Assert.assertEquals(false, boolResult);
     }

}

