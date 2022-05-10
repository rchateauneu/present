package paquetage;

import org.jinterop.dcom.common.IJIAuthInfo;
import org.jinterop.dcom.common.JISystem;
import org.jinterop.dcom.core.IJIComObject;
import org.jinterop.dcom.core.JISession;
import org.jinterop.dcom.core.JIComServer;
import org.jinterop.dcom.core.JIProgId;
import org.jinterop.dcom.common.JIException;

import java.net.UnknownHostException;
import java.util.logging.Level;

import org.jinterop.dcom.impls.JIObjectFactory;
import org.jinterop.dcom.impls.automation.IJIDispatch;
import org.junit.Test;

public class WmInteropTest {
    //@Test
    public void FirstTest()  throws JIException, UnknownHostException, java.io.IOException{

        // JIDefaultAuthInfoImpl(java.lang.String domain, java.lang.String username, java.lang.String password)

        // LAPTOP-R89KG6V1.broadband

        // org.jinterop.dcom.common.JIException: Access is denied, please check
        // whether the [domain-username-password] are correct.
        // Also, if not already done please check the GETTING STARTED and FAQ sections in readme.htm.
        // They provide information on how to correctly configure the Windows machine for DCOM access,
        // so as to avoid such exceptions.  [0x00000005]
        // JISession session = JISession.createSession("LAPTOP-R89KG6V1", "rchateau", "kennwert");

        /*
        C:\Users\rchat>whoami
        laptop-r89kg6v1\rchat

        JISystem.setAutoRegisteration(true);
        JISession session = JISession.createSession(System.getenv("USERDOMAIN"), login, password);
        session.useSessionSecurity(true);
        final JIComServer server = new JIComServer(JIProgId.valueOf(WBEM_PROGID), HOST, session);
         */
        String userDomain = System.getenv("USERDOMAIN");
        System.out.println("userDomain=" + userDomain);
        //JISystem.setAutoRegisteration(false);
        // org.jinterop.dcom.common.JIException: Access is denied, please check whether the [domain-username-password] are correct. Also, if not already done please check the GETTING STARTED and FAQ sections in readme.htm. They provide information on how to correctly configure the Windows machine for DCOM access, so as to avoid such exceptions.  [0x00000005]
        // JISession session = JISession.createSession("LAPTOP-R89KG6V1", "XXrchateauneu@hotmail.com", "Becon.47930789");
        // JISession session = JISession.createSession();

        // Y a du progres: org.jinterop.dcom.common.JIException: Access is denied.  [0x80070005]
        JISystem.getLogger().setLevel(Level.FINEST);
        JISystem.setInBuiltLogHandler(false);
        JISystem.setAutoRegisteration(false);
        JISession session = JISession.createSession("LAPTOP-R89KG6V1", "rchateauneu@hotmail.com", "Becon.47930789");
        //JISession session = JISession.createSession("", "asd", "SSSS");
        session.useSessionSecurity(true);
        System.out.println("session "+session);
        IJIAuthInfo authInfo = session.getAuthInfo();
        if(authInfo != null) {
            System.out.println("domain=" + authInfo.getDomain());
            System.out.println("username=" + authInfo.getUserName());
            System.out.println("password=" + authInfo.getPassword());
        }
        System.out.println("domain=" + session.getDomain());
        System.out.println("username=" + session.getUserName());
        System.out.println("session identifier=" + session.getSessionIdentifier());
        System.out.println("global socket timeout=" + session.getGlobalSocketTimeout());

        JIProgId progId = JIProgId.valueOf( "WbemScripting.SWbemLocator" );
        System.out.println("progId=" + progId.toString());
        // address = "glagla" : java.net.UnknownHostException: No such host is known (glagla)
        JIComServer wbemLocatorComObj = new JIComServer( progId, "LAPTOP-R89KG6V1", session );
        try {
            IJIComObject dispatch = JIObjectFactory.narrowObject( wbemLocatorComObj.createInstance().queryInterface( IJIDispatch.IID ) );
        }
        catch(JIException e)
        {
            e.printStackTrace();
            throw e;
        }

        //JISession session2 = JISession.createSession("", "rchateauneu@hotmail.com",
        //        "Becon.47930789");
        try {
            JIComServer comServer = new JIComServer(JIProgId.valueOf("Excel.Application"), "LAPTOP-R89KG6V1", session);
        }
        catch(JIException e)
        {
            e.printStackTrace();
            throw e;
        }
    }
}
