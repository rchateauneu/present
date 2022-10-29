package paquetage;

import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.COMUtils;
import COM.Wbemcli;
import COM.WbemcliUtil;
import com.sun.jna.platform.win32.OaIdl.SAFEARRAY;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;

import static com.sun.jna.platform.win32.Variant.VT_ARRAY;
import static com.sun.jna.platform.win32.Variant.VT_BSTR;

public class WmiJnaTest {

    @Before
    public void initCom() {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
        // assertEquals(COMUtils.S_OK, Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED).intValue());
        assertEquals(COMUtils.S_OK,
                Ole32.INSTANCE.CoInitializeSecurity(null, -1, null, null, Ole32.RPC_C_AUTHN_LEVEL_DEFAULT,
                        Ole32.RPC_C_IMP_LEVEL_IMPERSONATE, null, Ole32.EOAC_NONE, null).intValue());
    }

    @After
    public void unInitCom() {
        Ole32.INSTANCE.CoUninitialize();
    }

    @Test
    public void testSelectDiskDrives() {
        // Connect to the server
        Wbemcli.IWbemServices svc = WbemcliUtil.connectServer(WbemcliUtil.DEFAULT_NAMESPACE);

        // Send query
        try {
            Wbemcli.IEnumWbemClassObject enumerator = svc.ExecQuery(
                    "WQL",
                    "SELECT Caption, Capabilities, CapabilityDescriptions FROM Win32_DiskDrive",
                    Wbemcli.WBEM_FLAG_FORWARD_ONLY, null);

            try {
                Wbemcli.IWbemClassObject[] result;
                Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
                IntByReference pType = new IntByReference();
                IntByReference plFlavor = new IntByReference();
                while(true) {
                    result = enumerator.Next(0, 1);
                    if(result.length == 0) {
                        break;
                    }
                    COMUtils.checkRC(result[0].Get("Caption", 0, pVal, pType, plFlavor));
                    OleAuto.INSTANCE.VariantClear(pVal);
                    COMUtils.checkRC(result[0].Get("CapabilityDescriptions", 0, pVal, pType, plFlavor));
                    SAFEARRAY safeArray = (SAFEARRAY) pVal.getValue();
                    for(int i = safeArray.getLBound(0); i <= safeArray.getUBound(0); i++) {
                        System.out.println("\tCapabilityDescriptions " + safeArray.getElement(i));
                    }
                    OleAuto.INSTANCE.VariantClear(pVal);
                    COMUtils.checkRC(result[0].Get("Capabilities", 0, pVal, pType, plFlavor));
                    safeArray = (SAFEARRAY) pVal.getValue();
                    for(int i = safeArray.getLBound(0); i <= safeArray.getUBound(0); i++) {
                        System.out.println("\tCapabilities " + safeArray.getElement(i));
                    }
                    OleAuto.INSTANCE.VariantClear(pVal);
                    result[0].Release();
                }
            } finally {
                enumerator.Release();
            }
        } finally {
            svc.Release();
        }
    }

    @Test
    public void testSelectProcesses() {
        // Connect to the server
        Wbemcli.IWbemServices svc = WbemcliUtil.connectServer(WbemcliUtil.DEFAULT_NAMESPACE);

        // Send query
        try {
            Wbemcli.IEnumWbemClassObject enumerator = svc.ExecQuery(
                    "WQL",
                    "SELECT Handle FROM CIM_Process",
                    Wbemcli.WBEM_FLAG_FORWARD_ONLY, null);
            try {
                Wbemcli.IWbemClassObject[] result;
                Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
                IntByReference pType = new IntByReference();
                IntByReference plFlavor = new IntByReference();
                while(true) {
                    result = enumerator.Next(0, 1);
                    if(result.length == 0) {
                        break;
                    }
                    COMUtils.checkRC(result[0].Get("Handle", 0, pVal, pType, plFlavor));
                    OleAuto.INSTANCE.VariantClear(pVal);
                    result[0].Release();
                }
            } finally {
                enumerator.Release();
            }
        } finally {
            svc.Release();
        }
    }

    /**
     * Copy from WbemcliUtil#connectServer with American English selected as
     * locale.
     */
    private static Wbemcli.IWbemServices connectServerEnglishLocale(String namespace) {
        Wbemcli.IWbemLocator loc = Wbemcli.IWbemLocator.create();
        if (loc == null) {
            throw new COMException("Failed to create WbemLocator object.");
        }

        Wbemcli.IWbemServices services = loc.ConnectServer(namespace, null, null, "MS_409", 0, null, null);
        loc.Release();

        WinNT.HRESULT hres = Ole32.INSTANCE.CoSetProxyBlanket(services, Ole32.RPC_C_AUTHN_WINNT, Ole32.RPC_C_AUTHZ_NONE, null,
            Ole32.RPC_C_AUTHN_LEVEL_CALL, Ole32.RPC_C_IMP_LEVEL_IMPERSONATE, null, Ole32.EOAC_NONE);
        if (COMUtils.FAILED(hres)) {
                services.Release();
                throw new COMException("Could not set proxy blanket.", hres);
            }
        return services;
    }

    @Test
    public void testIWbemClassObjectGetQualifierSet() {

        Wbemcli.IWbemServices svc = null;
        Wbemcli.IEnumWbemClassObject enumRes = null;
        Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
        IntByReference pType = new IntByReference();
        IntByReference plFlavor = new IntByReference();

        boolean foundWin32_Process = false;
        try {
            svc = connectServerEnglishLocale(WbemcliUtil.DEFAULT_NAMESPACE);
            enumRes = svc.ExecQuery(
                    "WQL",
                    "SELECT * FROM meta_class",
                    COM.Wbemcli.WBEM_FLAG_FORWARD_ONLY | COM.Wbemcli.WBEM_FLAG_USE_AMENDED_QUALIFIERS, null);

            while (true) {
                Wbemcli.IWbemClassObject[] results = enumRes.Next(Wbemcli.WBEM_INFINITE, 1);
                if (results.length == 0) {
                    break;
                }

                Wbemcli.IWbemClassObject classObject = results[0];
                Variant.VARIANT.ByReference pQualifierVal = new Variant.VARIANT.ByReference();

                COMUtils.checkRC(classObject.Get("__CLASS", 0, pVal, pType, plFlavor));
                String className = pVal.stringValue();
                if(! className.equals("Win32_Process")) {
                    continue;
                }
                foundWin32_Process = true;
                OleAuto.INSTANCE.VariantClear(pVal);

                COMUtils.checkRC(classObject.Get("__SUPERCLASS", 0, pVal, pType, plFlavor));
                Object baseClass = pVal.getValue();
                OleAuto.INSTANCE.VariantClear(pVal);
                assertEquals("CIM_Process", baseClass.toString());

                String[] propertyNames = classObject.GetNames(null, 0, pQualifierVal);
                assertTrue(Arrays.asList(propertyNames).contains("ProcessId"));

                Wbemcli.IWbemQualifierSet classQualifiersSet = classObject.GetQualifierSet();
                String[] classQualifiersNames = classQualifiersSet.GetNames();
                assertTrue(Arrays.asList(classQualifiersNames).contains("DisplayName"));
                String classDisplayName = classQualifiersSet.Get("DisplayName");
                assertEquals("Processes", classDisplayName);

                Wbemcli.IWbemQualifierSet propertyQualifiersSet = classObject.GetPropertyQualifierSet("ProcessId");
                String[] propertyQualifierNames = propertyQualifiersSet.GetNames();

                assertTrue(Arrays.asList(propertyQualifierNames).contains("DisplayName"));
                String propertyDisplayName = propertyQualifiersSet.Get("DisplayName");
                assertEquals("Process Id", propertyDisplayName);

                assertTrue(Arrays.asList(propertyQualifierNames).contains("CIMTYPE"));
                String propertyCIMTYPE = propertyQualifiersSet.Get("CIMTYPE");
                assertEquals("uint32", propertyCIMTYPE);

                classObject.Release();
            }
        } finally {
            if (svc != null) svc.Release();
            if (enumRes != null) enumRes.Release();
        }
        assertTrue(foundWin32_Process);
    }

    @Test
    public void testIWbemContextSetValue() {
        long currentPid = Kernel32.INSTANCE.GetCurrentProcessId();
        String objectPath = String.format("\\\\.\\%s:Win32_Process.Handle=\"%d\"", WbemcliUtil.DEFAULT_NAMESPACE, currentPid);

        // This context object retrieves only parts of a WMI instance.
        Wbemcli.IWbemContext pctxDrive = new Wbemcli.IWbemContext().create();
        pctxDrive.SetValue("__GET_EXTENSIONS", 0, true);
        pctxDrive.SetValue("__GET_EXT_CLIENT_REQUEST", 0, true);

        // Create a safe array of just one property to retrieve.
        OaIdl.SAFEARRAY psaProperties = OaIdl.SAFEARRAY.createSafeArray(new WTypes.VARTYPE(VT_BSTR), 1);
        OleAuto.INSTANCE.SafeArrayLock(psaProperties);
        try {
            WTypes.BSTR strPropertyBSTR = OleAuto.INSTANCE.SysAllocString("ProcessId");
            try {
                psaProperties.putElement(strPropertyBSTR, 0);
            } finally {
                OleAuto.INSTANCE.SysFreeString(strPropertyBSTR);
            }
        } finally {
            OleAuto.INSTANCE.SafeArrayUnlock(psaProperties);
        }

        Variant.VARIANT.ByReference vPropertyList = new Variant.VARIANT.ByReference();
        vPropertyList.setVarType((short) (VT_ARRAY | VT_BSTR));
        vPropertyList.setValue(psaProperties);
        pctxDrive.SetValue("__GET_EXT_PROPERTIES", 0, vPropertyList);
        psaProperties.destroy();

        Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
        Wbemcli.IWbemServices svc = null;
        try {
            svc = WbemcliUtil.connectServer(WbemcliUtil.DEFAULT_NAMESPACE);
            Wbemcli.IWbemClassObject classObject = svc.GetObject(objectPath, Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, pctxDrive);
            // The properties "Handle" and "PropertyId" must have the same values with different types.
            COMUtils.checkRC(classObject.Get("ProcessId", 0, pVal, null, null));
        }
        finally {
            if (svc != null) svc.Release();
        }
        assertEquals(currentPid, pVal.longValue());
    }
}
