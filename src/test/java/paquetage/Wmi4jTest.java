package paquetage;

//import com.profesorfalken.WMI4Java;
import org.junit.Test;
import java.net.UnknownHostException;

import cn.chenlichao.wmi4j.*;

public class Wmi4jTest {
    // @Test
    public void OneWmiTest() throws WMIException, UnknownHostException {
        String server = "localhost"; // "192.168.1.201";
        String username = ""; // ""administrator";
        String password = ""; // ""password";
        String namespace = "root\\cimv2";


        // SWbemLocator locator = new SWbemLocator(server,username,password,namespace);
        // cn.chenlichao.wmi4j.WMIException: The system cannot find the file specified.
        // Please check the path provided as parameter. If this exception is being thrown from the WinReg package,
        // please check if the library is registered properly or do so using regsvr32. [0x00000002]
        SWbemLocator locator = new SWbemLocator("LAPTOP-R89KG6V1","rchateauneu@hotmail.com",
                "Becon.47930789",namespace);

        SWbemServices services = locator.connectServer();
        SWbemObject object = services.get("Win32_Service.Name='AppMgmt'");

        //print AppMgmt properties
        System.out.println(object.getObjectText());

        //print AppMgmt service state
        System.out.println(object.getPropertyByName("State").getStringValue());

        //Stop AppMgmt service
        object.execMethod("Stop");
        /*
        } catch (WMIException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        */
    }
}
