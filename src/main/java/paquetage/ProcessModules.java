package paquetage;

import java.util.*;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.W32APIOptions;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

interface ProcessPathKernel32 extends Kernel32 {
    class MODULEENTRY32 extends Structure {
        public static class ByReference extends MODULEENTRY32 implements Structure.ByReference {
            public ByReference() {
            }

            public ByReference(Pointer memory) {
                super(memory);
            }
        }
        public MODULEENTRY32() {
            dwSize = new WinDef.DWORD(size());
        }

        public MODULEENTRY32(Pointer memory) {
            super(memory);
            read();
        }


        public DWORD dwSize;
        public DWORD th32ModuleID;
        public DWORD th32ProcessID;
        public DWORD GlblcntUsage;
        public DWORD ProccntUsage;
        public Pointer modBaseAddr;
        public DWORD modBaseSize;
        public HMODULE hModule;
        public char[] szModule = new char[255+1]; // MAX_MODULE_NAME32
        public char[] szExePath = new char[MAX_PATH];
        public String szModule() { return Native.toString(this.szModule); }
        public String szExePath() { return Native.toString(this.szExePath); }
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(new String[] {
                    "dwSize", "th32ModuleID", "th32ProcessID", "GlblcntUsage", "ProccntUsage", "modBaseAddr", "modBaseSize", "hModule", "szModule", "szExePath"
            });
        }
    }

    ProcessPathKernel32 INSTANCE = (ProcessPathKernel32)Native.loadLibrary(ProcessPathKernel32.class, W32APIOptions.UNICODE_OPTIONS);
    boolean Module32First(HANDLE hSnapshot, MODULEENTRY32.ByReference lpme);
    boolean Module32Next(HANDLE hSnapshot, MODULEENTRY32.ByReference lpme);
}

public class ProcessModules {

    public static Map<String, ArrayList<String>> GetAll() {
        HashMap<String, ArrayList<String>> result = new HashMap<>();

        Kernel32 kernel32 = (Kernel32) Native.loadLibrary(Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);
        Tlhelp32.PROCESSENTRY32.ByReference processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();
        WinNT.HANDLE processSnapshot =
                kernel32.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        try {

            while (kernel32.Process32Next(processSnapshot, processEntry)) {
                // looks for a specific process
                // if (Native.toString(processEntry.szExeFile).equalsIgnoreCase("textpad.exe")) {
                ArrayList<String> newList = new ArrayList<>();
                //System.out.println(processEntry.th32ProcessID + "\t" + Native.toString(processEntry.szExeFile));
                WinNT.HANDLE moduleSnapshot =
                        kernel32.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPMODULE, processEntry.th32ProcessID);
                try {
                    ProcessPathKernel32.MODULEENTRY32.ByReference me = new ProcessPathKernel32.MODULEENTRY32.ByReference();
                    if (ProcessPathKernel32.INSTANCE.Module32First(moduleSnapshot, me)) {
                        //System.out.println("\t" + me.szExePath() );
                        //newList.add(me.szExePath());
                        do {
                            newList.add(me.szExePath());
                            //System.out.println("\t" + me.szExePath());
                        } while (ProcessPathKernel32.INSTANCE.Module32Next(moduleSnapshot, me));
                    }
                }
                finally {
                    kernel32.CloseHandle(moduleSnapshot);
                }
                result.put(processEntry.th32ProcessID.toString(), newList);
            }
        }
        finally {
            kernel32.CloseHandle(processSnapshot);
        }
        return result;
    }
}