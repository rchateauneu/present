package paquetage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.W32APIOptions;

import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinNT;
import org.apache.log4j.Logger;

import java.time.LocalTime;

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
    final static private Logger logger = Logger.getLogger(ProcessModules.class);

    private Kernel32 kernel32 = (Kernel32) Native.loadLibrary(Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);

    /** This returns all pids and for each pid, the modules it is linked to.
     * The first module of each array is the executable.
     * @return
     */
    private Map<String, ArrayList<String>> GetAllProcessesModulesCached() {
        logger.debug("Getting processes and modules");
        HashMap<String, ArrayList<String>> result = new HashMap<>();

        Tlhelp32.PROCESSENTRY32.ByReference processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();
        WinNT.HANDLE processSnapshot =
                kernel32.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        try {

            while (kernel32.Process32Next(processSnapshot, processEntry)) {
                ArrayList<String> modulesList = GetFromPidDWord(processEntry.th32ProcessID);
                result.put(processEntry.th32ProcessID.toString(), modulesList);
            }
        }
        finally {
            kernel32.CloseHandle(processSnapshot);
        }
        logger.debug("Processes:" + result.size());
        return result;
    }

    private Map<String, ArrayList<String>> CacheAllProcessesModules = null;
    private LocalTime CacheUpdateTime = null;

    public Map<String, ArrayList<String>> GetAllProcessesModules() {
        LocalTime nowTime = LocalTime.now();
        if(CacheAllProcessesModules != null) {
            Duration duration = Duration.between(CacheUpdateTime, nowTime);
            // The validity of the cache is one second, which is reasonable given the time
            // taken by a process to start, and the fact that this query cannot be fully up-to-date.
            // TODO: A better solution would be to detect creation and destruction of processes,
            // TODO and loading of modules, then accordingly update the cache.
            if (duration.getSeconds() < 1) {
                return CacheAllProcessesModules;
            }
        }
        CacheAllProcessesModules = GetAllProcessesModulesCached();
        CacheUpdateTime = nowTime;
        return CacheAllProcessesModules;
    }


    /** This returns an array of all pids using a given module.
     * It iterates on all processes, then for each process, on its modules.
     * This is slower than getting a snapshot will all processes,
     * and much slower than using the cache of all processes and modules.
     *
     * @param moduleName The file name.
     * @return An array of pids as strings.
     */
    public List<String> GetFromModule_NoCache(String moduleName)
    {
        ArrayList<String> pidsList = new ArrayList<>();

        Tlhelp32.PROCESSENTRY32.ByReference processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();
        // Snapshot on all processes.
        WinNT.HANDLE processSnapshot =
                kernel32.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        ProcessPathKernel32.MODULEENTRY32.ByReference me = new ProcessPathKernel32.MODULEENTRY32.ByReference();
        try {
            while (kernel32.Process32Next(processSnapshot, processEntry)) {
                // Snapshot on all modules of this process.
                WinNT.HANDLE moduleSnapshot = kernel32.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPMODULE, processEntry.th32ProcessID);
                try {
                    // ProcessPathKernel32.MODULEENTRY32.ByReference me = new ProcessPathKernel32.MODULEENTRY32.ByReference();
                    if (ProcessPathKernel32.INSTANCE.Module32First(moduleSnapshot, me)) {
                        do {
                            if( moduleName.equals(me.szExePath())) {
                                pidsList.add(processEntry.th32ProcessID.toString());
                                // No need to look further.
                                break;
                            }
                        } while (ProcessPathKernel32.INSTANCE.Module32Next(moduleSnapshot, me));
                    }
                }
                finally {
                    kernel32.CloseHandle(moduleSnapshot);
                }
            }
        }
        finally {
            kernel32.CloseHandle(processSnapshot);
        }

        return pidsList;
    }

    /** This does the same as GetFromModule_VersionA but gets a complete snapshot then does the selection.
     *
     * @param moduleName
     * @return
     */
    public List<String> GetFromModule(String moduleName) {
        ArrayList<String> pidsList = new ArrayList<>();

        Map<String, ArrayList<String>> allProcess = GetAllProcessesModules();
        for(Map.Entry<String, ArrayList<String>> processEntry : allProcess.entrySet()) {
            if(processEntry.getValue().contains(moduleName)) {
                pidsList.add(processEntry.getKey());
            }
        }

        return pidsList;
    }


    /** This returns the array of all modules used by a process.
     *
     * @param intProc The pid as DWORD.
     * @return An array of module, the first one being the executable and is always present.
     */
    public ArrayList<String> GetFromPidDWord(WinDef.DWORD intProc)
    {
        ArrayList<String> modulesList = new ArrayList<>();

        WinNT.HANDLE moduleSnapshot = kernel32.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPMODULE, intProc);
        try {
            ProcessPathKernel32.MODULEENTRY32.ByReference me = new ProcessPathKernel32.MODULEENTRY32.ByReference();
            if (ProcessPathKernel32.INSTANCE.Module32First(moduleSnapshot, me)) {
                do {
                    modulesList.add(me.szExePath());
                } while (ProcessPathKernel32.INSTANCE.Module32Next(moduleSnapshot, me));
            }
        }
        finally {
            kernel32.CloseHandle(moduleSnapshot);
        }

        return modulesList;
    }

    public List<String> GetFromPid(String strPid) {
        WinDef.DWORD intProc = new WinDef.DWORD(Long.parseLong(strPid));
        return GetFromPidDWord(intProc);
    }

}