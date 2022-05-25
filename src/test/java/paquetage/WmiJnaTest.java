package paquetage;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Wbemcli;
import com.sun.jna.platform.win32.COM.WbemcliUtil;
import com.sun.jna.platform.win32.OaIdl.SAFEARRAY;
import com.sun.jna.platform.win32.WTypes.BSTR;
import com.sun.jna.platform.win32.WTypes.LPOLESTR;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.net.UnknownHostException;
import java.util.logging.Level;

import org.jinterop.dcom.impls.JIObjectFactory;
import org.jinterop.dcom.impls.automation.IJIDispatch;
import org.junit.Test;

enum Win32_DiskDrive_Values {
    Caption,
    Capabilities
}

public class WmiJnaTest {
    //@Test
    public void DoTheTest() {
        WbemcliUtil.WmiQuery<Win32_DiskDrive_Values> serialNumberQuery = new WbemcliUtil.WmiQuery<Win32_DiskDrive_Values>("Win32_DiskDrive", Win32_DiskDrive_Values.class);
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
        WbemcliUtil.WmiResult<Win32_DiskDrive_Values> result = serialNumberQuery.execute();
        for (int i = 0; i < result.getResultCount(); i++) {
            //System.out.println(result.getValue(Win32_DiskDrive_Values.Caption, i));
            SAFEARRAY value = (OaIdl.SAFEARRAY) result.getValue(Win32_DiskDrive_Values.Capabilities, i);
            // According to https://docs.microsoft.com/en-us/windows/desktop/cimwin32prov/win32-diskdrive, the type of Capabilities
            // should be uint16[] which should be Variant.VT_I2 (2-byte integer)
            // however, it is not constant. sometimes it is 0, sometimes Variant.VT_I2 (3);
            //System.out.println("Var Type(3 expected): " + value.getVarType().intValue());
            //System.out.println("Size (>0 expected): " + (value.getUBound(0) - value.getLBound(0)));
            Object el = value.getElement(0);
            //System.out.println("Element 0 (!=null expected): " + el);
            Pointer pointer = value.accessData();
            //System.out.println("pointer (!=null expected): " + pointer);
        }
    }

    @Test
    public void SelectDiskDrives() {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);

        // Connect to the server
        Wbemcli.IWbemServices svc = WbemcliUtil.connectServer("ROOT\\CIMV2");

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
                    for(int i = safeArray.getLBound(0); i<=safeArray.getUBound(0); i++) {
                        System.out.println("\tCapabilityDescriptions " + safeArray.getElement(i));
                    }
                    OleAuto.INSTANCE.VariantClear(pVal);
                    COMUtils.checkRC(result[0].Get("Capabilities", 0, pVal, pType, plFlavor));
                    safeArray = (SAFEARRAY) pVal.getValue();
                    for(int i = safeArray.getLBound(0); i<=safeArray.getUBound(0); i++) {
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

        Ole32.INSTANCE.CoUninitialize();
    }

    @Test
    public void SelectProcesses() {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);

        // Connect to the server
        Wbemcli.IWbemServices svc = WbemcliUtil.connectServer("ROOT\\CIMV2");

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
                    System.out.println("---------" + pVal.getValue() + "-------------");
                    OleAuto.INSTANCE.VariantClear(pVal);
                    result[0].Release();
                }
            } finally {
                enumerator.Release();
            }
        } finally {
            svc.Release();
        }

        Ole32.INSTANCE.CoUninitialize();
    }

}
