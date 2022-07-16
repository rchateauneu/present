package paquetage;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;

abstract class Provider {
    public abstract boolean MatchQuery(QueryData queryData);

    // This assumes that all needed columns can be calculated.
    public abstract ArrayList<GenericSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception;

    // TODO: Estimate cost.
}

// TODO: Test separately these providers.

class Provider_CIM_DataFile_Name extends Provider {
    public boolean MatchQuery(QueryData queryData)
    {
        return queryData.CompatibleQuery("CIM_DataFile", Set.of("Name"));
    }

    public ArrayList<GenericSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericSelecter.Row> result = new ArrayList<>();
        String fileName = queryData.GetWhereValue("Name");
        String pathFile = ObjectPath.BuildPathWbem("CIM_DataFile", Map.of("Name", fileName));
        GenericSelecter.Row singleRow = new GenericSelecter.Row();

        ObjectGetter_Cim_DataFile_Name.FillRowFromQueryAndFilename(singleRow, queryData, fileName);

        // Add the main variable anyway.
        singleRow.PutNode(queryData.mainVariable, pathFile);

        result.add(singleRow);
        return result;
    }
}

class Provider_CIM_DirectoryContainsFile_PartComponent extends Provider {
    public boolean MatchQuery(QueryData queryData) {
        return queryData.CompatibleQuery("CIM_DirectoryContainsFile", Set.of("PartComponent"));
    }
    public ArrayList<GenericSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericSelecter.Row> result = new ArrayList<>();
        String valuePartComponent = queryData.GetWhereValue("PartComponent");
        Map<String, String> properties = ObjectPath.ParseWbemPath(valuePartComponent);
        String filePath = properties.get("Name");
        File file = new File(filePath);
        String parentPath = file.getAbsoluteFile().getParent();
        String pathDirectory = ObjectPath.BuildPathWbem("Win32_Directory", Map.of("Name", parentPath));

        GenericSelecter.Row singleRow = new GenericSelecter.Row();
        String variableName = queryData.ColumnToVariable("GroupComponent");
        singleRow.PutNode(variableName, pathDirectory);

        // It must also the path of the associator row, even if it will probably not be used.
        String pathAssoc = ObjectPath.BuildPathWbem(
                "CIM_DirectoryContainsFile", Map.of(
                        "PartComponent", valuePartComponent,
                        "GroupComponent", pathDirectory));
        singleRow.PutNode(queryData.mainVariable, pathAssoc);

        result.add(singleRow);
        return result;
    }
}

class Provider_CIM_DirectoryContainsFile_GroupComponent extends Provider {
    public boolean MatchQuery(QueryData queryData) {
        return queryData.CompatibleQuery("CIM_DirectoryContainsFile", Set.of("GroupComponent"));
    }
    public ArrayList<GenericSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericSelecter.Row> result = new ArrayList<>();
        String valueGroupComponent = queryData.GetWhereValue("GroupComponent");
        Map<String, String> properties = ObjectPath.ParseWbemPath(valueGroupComponent);
        String dirPath = properties.get("Name");
        String pathGroupComponent = ObjectPath.BuildPathWbem("Win32_Directory", Map.of("Name", dirPath));

        String variableName = queryData.ColumnToVariable("PartComponent");

        File path = new File(dirPath);

        File [] files = path.listFiles();
        for (File aFile : files){
            if (! aFile.isFile()){
                continue;
            }
            String fileName = aFile.getAbsoluteFile().toString();

            GenericSelecter.Row singleRow = new GenericSelecter.Row();

            String valuePartComponent = ObjectPath.BuildPathWbem(
                    "CIM_DataFile", Map.of(
                            "Name", fileName));
            singleRow.PutNode(variableName, valuePartComponent);

            // It must also the path of the associator row, even if it will probably not be used.
            String pathAssoc = ObjectPath.BuildPathWbem(
                    "CIM_DirectoryContainsFile", Map.of(
                            "PartComponent", valuePartComponent,
                            "GroupComponent", pathGroupComponent));
            singleRow.PutNode(queryData.mainVariable, pathAssoc);

            result.add(singleRow);
        }

        return result;
    }
}

/** The input is a module, a filename. It returns processes using it.
 *
 */
class Provider_CIM_ProcessExecutable_Antecedent extends Provider {
    static ProcessModules processModules = new ProcessModules();

    public boolean MatchQuery(QueryData queryData) {
        return queryData.CompatibleQuery("CIM_ProcessExecutable", Set.of("Antecedent"));
    }
    public ArrayList<GenericSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericSelecter.Row> result = new ArrayList<>();
        String valueAntecedent = queryData.GetWhereValue("Antecedent");
        Map<String, String> properties = ObjectPath.ParseWbemPath(valueAntecedent);
        String filePath = properties.get("Name");
        List<String> listPids = processModules.GetFromModule(filePath);

        String variableName = queryData.ColumnToVariable("Dependent");
        for(String onePid : listPids) {
            GenericSelecter.Row singleRow = new GenericSelecter.Row();
            String pathDependent = ObjectPath.BuildPathWbem("Win32_Process", Map.of("Handle", onePid));
            singleRow.PutNode(variableName, pathDependent);

            // It must also the path of the associator row, even if it will probably not be used.
            String pathAssoc = ObjectPath.BuildPathWbem(
                    "CIM_ProcessExecutable", Map.of(
                            "Dependent", pathDependent,
                            "Antecedent", valueAntecedent));
            singleRow.PutNode(queryData.mainVariable, pathAssoc);

            result.add(singleRow);
        }

        return result;
    }
}

/** The input is a process and it returns its executable and libraries.
 *
 */
class Provider_CIM_ProcessExecutable_Dependent extends Provider {
    static ProcessModules processModules = new ProcessModules();

    public boolean MatchQuery(QueryData queryData) {
        return queryData.CompatibleQuery("CIM_ProcessExecutable", Set.of("Dependent"));
    }
    public ArrayList<GenericSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericSelecter.Row> result = new ArrayList<>();
        String valueDependent = queryData.GetWhereValue("Dependent");
        Map<String, String> properties = ObjectPath.ParseWbemPath(valueDependent);
        String pidStr = properties.get("Handle");
        List<String> listModules = processModules.GetFromPid(pidStr);

        String variableName = queryData.ColumnToVariable("Antecedent");
        for(String oneFile : listModules) {
            GenericSelecter.Row singleRow = new GenericSelecter.Row();
            String pathAntecedent = ObjectPath.BuildPathWbem("CIM_DataFile", Map.of("Name", oneFile));
            singleRow.PutNode(variableName, pathAntecedent);

            // It must also the path of the associator row, even if it will probably not be used.
            String pathAssoc = ObjectPath.BuildPathWbem(
                    "CIM_ProcessExecutable", Map.of(
                            "Dependent", valueDependent,
                            "Antecedent", pathAntecedent));
            singleRow.PutNode(queryData.mainVariable, pathAssoc);

            result.add(singleRow);
        }

        return result;
    }
}

abstract class ObjectGetter {
    public abstract boolean MatchGet(QueryData queryData);

    // This assumes that all needed columns can be calculated.
    public abstract GenericSelecter.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception;

    // TODO: Cost estimation.
}

class ObjectGetter_Cim_DataFile_Name extends ObjectGetter {
        /* Possibly required fields:
            Caption;
            Description;
            InstallDate;
            Status;
            AccessMask;
            Archive;
            Compressed;
            CompressionMethod;
            CreationClassName;
            CreationDate;
            CSCreationClassName;
            CSName;
            Drive;
            EightDotThreeFileName;
            Encrypted;
            EncryptionMethod;
            Name;
            Extension;
            FileName;
            FileSize;
            FileType;
            FSCreationClassName;
            FSName;
            Hidden;
            InUseCount;
            LastAccessed;
            LastModified;
            Path;
            Readable;
            System;
            Writeable;
            Manufacturer;
            Version;
         */

    // Get-WmiObject -Query 'select Drive from CIM_DataFile where Name="C:\\WINDOWS\\SYSTEM32\\ntdll.dll"'
    public boolean MatchGet(QueryData queryData) {
        return queryData.ColumnsSubsetOf("CIM_DataFile", columnsMap.keySet());
    }

    static String FileToDrive(String fileName) {
        Path p = Paths.get(fileName);
        String driveStrRaw = p.getRoot().toString();
        return driveStrRaw.toLowerCase().substring(0, driveStrRaw.length()-1);
    }

    static String FileToName(String fileName) {
        Path p = Paths.get(fileName);
        String fileNameShort = p.getFileName().toString();
        return fileNameShort.substring(0, fileNameShort.lastIndexOf("."));
    }

    static String FileToSize(String fileName) {
        File f = new File(fileName);
        long fileSize = f.length();
        return Long.toString(fileSize);
    }

    static String FileToPath(String fileName) {
        Path p = Paths.get(fileName);
        return p.getParent().toString().toLowerCase().substring(2) + "\\";
    }

    static Map<String, Function<String, String>> columnsMap = Map.of(
            "Caption", (String fileName) -> fileName,
            "Drive", (String fileName) -> FileToDrive(fileName),
            "FileName", (String fileName) -> FileToName(fileName),
            "FileSize", (String fileName) -> FileToSize(fileName),
            // "FileType", (String fileName) -> "Application Extension",
            "Path", (String fileName) -> FileToPath(fileName)
            );

    public static void FillRowFromQueryAndFilename(GenericSelecter.Row singleRow, QueryData queryData, String fileName) {
        for(Map.Entry<String, String> qCol : queryData.queryColumns.entrySet()) {
            Function<String, String> lambda = columnsMap.get(qCol.getKey());
            String variableValue = lambda.apply(fileName); // columnsMap.get)
            // These columns do not return a path, so a string is OK.
            singleRow.PutString(qCol.getValue(), variableValue);
        }
    }

    public GenericSelecter.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception {
        Map<String, String> properties = ObjectPath.ParseWbemPath(objectPath);
        String fileName = properties.get("Name");
        GenericSelecter.Row singleRow = new GenericSelecter.Row();
        FillRowFromQueryAndFilename(singleRow, queryData, fileName);

        // It must also the path of the variable of the object, because it may be used by an associator.
        String pathFile = ObjectPath.BuildPathWbem(
                "CIM_DataFile", Map.of(
                        "Name", fileName));
        singleRow.PutNode(queryData.mainVariable, pathFile);
        return singleRow;
    }
}

class ObjectGetter_Win32_Process_Handle extends ObjectGetter {
        /*
        string   CreationClassName;
        string   Caption;
        string   CommandLine;
        datetime CreationDate;
        string   CSCreationClassName;
        string   CSName;
        string   Description;
        string   ExecutablePath;
        uint16   ExecutionState;
        string   Handle;
        uint32   HandleCount;
        datetime InstallDate;
        uint64   KernelModeTime;
        uint32   MaximumWorkingSetSize;
        uint32   MinimumWorkingSetSize;
        string   Name;
        string   OSCreationClassName;
        string   OSName;
        uint64   OtherOperationCount;
        uint64   OtherTransferCount;
        uint32   PageFaults;
        uint32   PageFileUsage;
        uint32   ParentProcessId;
        uint32   PeakPageFileUsage;
        uint64   PeakVirtualSize;
        uint32   PeakWorkingSetSize;
        uint32   Priority;
        uint64   PrivatePageCount;
        uint32   ProcessId;
        uint32   QuotaNonPagedPoolUsage;
        uint32   QuotaPagedPoolUsage;
        uint32   QuotaPeakNonPagedPoolUsage;
        uint32   QuotaPeakPagedPoolUsage;
        uint64   ReadOperationCount;
        uint64   ReadTransferCount;
        uint32   SessionId;
        string   Status;
        datetime TerminationDate;
        uint32   ThreadCount;
        uint64   UserModeTime;
        uint64   VirtualSize;
        string   WindowsVersion;
        uint64   WorkingSetSize;
        uint64   WriteOperationCount;
        uint64   WriteTransferCount;
        */

        /** This tells if this class can calculate the required columns. &*/
        public boolean MatchGet(QueryData queryData) {
            return queryData.ColumnsSubsetOf("Win32_Process", columnsMap.keySet());
        }

        static ProcessModules processModules = new ProcessModules();

        static String ProcessToName(String processId) {
                List<String> listModules = processModules.GetFromPid(processId);
                String executablePath = listModules.get(0);
                Path p = Paths.get(executablePath);
                String fileNameShort = p.getFileName().toString();
                return fileNameShort;
        }

        /** Windows version and build number. */
        static String WindowsVersion(String processId) {
            Kernel32 kernel = Kernel32.INSTANCE;
            WinNT.OSVERSIONINFOEX vex = new WinNT.OSVERSIONINFOEX();
            if (kernel.GetVersionEx(vex)) {
                return MessageFormat.format("{0}.{1}.{2}",
                        vex.dwMajorVersion.toString(),
                        vex.dwMinorVersion.toString(),
                        vex.dwBuildNumber.toString());
            } else {
                return "Cannot get  Windows version";
            }
        }

        /** This contains the columns that this class can calculate, plus the lambda available. */
        static Map<String, Function<String, String>> columnsMap = Map.of(
                "Handle", (String processId) -> processId,
                "Name", (String processId) -> ProcessToName(processId),
                "ProcessId", (String processId) -> processId,
                "WindowsVersion", (String processId) -> WindowsVersion(processId)
        );

        public static void FillRowFromQueryAndPid(GenericSelecter.Row singleRow, QueryData queryData, String processId) throws Exception {
            for(Map.Entry<String, String> qCol : queryData.queryColumns.entrySet()) {
                Function<String, String> lambda = columnsMap.get(qCol.getKey());
                if(lambda == null) {
                    throw new Exception("Cannot find lambda for " + qCol.getKey());
                }
                String variableValue = lambda.apply(processId);
                // These columns do not return a path, so a string is OK.
                singleRow.PutString(qCol.getValue(), variableValue);
            }
    }

    public GenericSelecter.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception {
        Map<String, String> properties = ObjectPath.ParseWbemPath(objectPath);
        String processId = properties.get("Handle");
        GenericSelecter.Row singleRow = new GenericSelecter.Row();
        FillRowFromQueryAndPid(singleRow, queryData, processId);

        // It must also the path of the variable of the object, because it may be used by an associator.
        String pathFile = ObjectPath.BuildPathWbem(
                "Win32_Process", Map.of(
                        "Handle", processId));
        singleRow.PutNode(queryData.mainVariable, pathFile);
        return singleRow;
    }
}


public class GenericSelecter {
    final static private Logger logger = Logger.getLogger(GenericSelecter.class);

    /** This is a special value type for Survol, to bridge values between WMI/WBEM and RDF.
     * The most important feature is NODE_TYPE which models a WBEM path and an IRI.
     */
    public enum ValueType {
        STRING_TYPE,
        INT_TYPE,
        FLOAT_TYPE,
        NODE_TYPE
    }

    /**
     * This is a row returned by a WMI select query.
     * For simplicity, each row contains all column names.
     * This is equivalent to a RDF4J BindingSet : a set of named value bindings, used to represent a query solution.
     * Values are indexed by name of the binding corresponding to the variables names in the projection of the query.
     *
     * TODO: When returning List<Row>, store the variable names once only, in a header. All rows have the same binding.
     */
    public static class Row {
        /** In this library, only the string value is used because this is what is needed for WQL.
         * WQL does not really manipulate floats or dates, so string conversion is OK.
         *
         * However, the type is needed when converting these values to RDF, specifically because of WBEM paths which
         * are transformed into RDF IRIs. Also, it might be needed to convert string values to RDF types: ints etc...
         * and for this, their original type is needed.
         *
         * @param Value
         * @param Type
         */
        public record ValueTypePair (String Value, ValueType Type) {};

        private Map<String, ValueTypePair> Elements;

        public ValueTypePair TryValueType(String key) {
            ValueTypePair vtp = Elements.get(key);
            // This is just a hint to check that wbem paths are correctly typed.
            if(vtp != null && vtp.Type != ValueType.NODE_TYPE && vtp.Value != null
                    && vtp.Value.startsWith("\\\\")
                    && ! vtp.Value.startsWith("\\\\?\\")
            ) {
                throw new RuntimeException("TryValueType: Key=" + key + " looks like a node:" + vtp.Value);
            }
            return vtp;
        }

        public ValueTypePair GetValueType(String key) {
            ValueTypePair value = TryValueType(key);
            if(value == null) {
                throw new RuntimeException("Unknown variable " + key + ". Vars=" + KeySet());
            }
            return value;
        }

        public String GetStringValue(String key) {
            return GetValueType(key).Value;
        }

        public void PutString(String key, String str) {
            if(str == null) {
                logger.warn("PutString: Key=" + key + " null value");
            } else if(str.startsWith("\\\\")) {
                // This is a hint which might not always work, but helps finding problems.
                throw new RuntimeException("PutString: Key=" + key + " looks like a node:" + str);
            }
            Elements.put(key, new ValueTypePair(str, ValueType.STRING_TYPE));
        }
        public void PutNode(String key, String str) {
            Elements.put(key, new ValueTypePair(str, ValueType.NODE_TYPE));
        }

        public void PutValueType(String key, ValueTypePair pairValueType) {
            // This is just a hint to detect that Wbem paths are correctly typed.
            // It also checks for "\\?\Volume{e88d2f2b-332b-4eeb-a420-20ba76effc48}\" which is not a path.
            if(pairValueType != null && pairValueType.Type != ValueType.NODE_TYPE && pairValueType.Value != null
                    && pairValueType.Value.startsWith("\\\\")
                    && ! pairValueType.Value.startsWith("\\\\?\\")
            ) {
                throw new RuntimeException("PutValueType: Key=" + key + " looks like a node:" + pairValueType.Value);
            }
            Elements.put(key, pairValueType);
        }

        public long ElementsSize() {
            return Elements.size();
        }

        public Set<String> KeySet() {
            return Elements.keySet();
        }

        public boolean ContainsKey(String key) {
            return Elements.containsKey(key);
        }

        public Row() {
            Elements = new HashMap<>();
        }

        /**
         * This is for testing. An input row is inserted, then triples are created using an existing list of patterns.
         * @param elements
         */
        public Row(Map<String, ValueTypePair> elements) {
            Elements = elements;
        }

        /** This is used to insert the result of a Sparql query execution.
         *
         * @param bindingSet
         */
        public Row(BindingSet bindingSet) {
            Elements = new HashMap<>();
            for (Iterator<Binding> it = bindingSet.iterator(); it.hasNext(); ) {
                Binding binding = it.next();
                Value bindingValue = binding.getValue();
                ValueType valueType = bindingValue.isIRI() ? ValueType.NODE_TYPE : ValueType.STRING_TYPE;
                PutValueType(binding.getName(), new ValueTypePair(binding.getValue().toString(), valueType));
            }
        }

        public String toString() {
            return Elements.toString();
        }
    }

    WmiSelecter wmiSelecter = new WmiSelecter();

    Provider[] providers = {
        new Provider_CIM_DataFile_Name(),
        new Provider_CIM_DirectoryContainsFile_PartComponent(),
        new Provider_CIM_DirectoryContainsFile_GroupComponent(),
        new Provider_CIM_ProcessExecutable_Dependent(),
        new Provider_CIM_ProcessExecutable_Antecedent()
    };

    public GenericSelecter()
    {}

    // This avoids to display the same message again and again.
    static Set<String> foundProviders = new HashSet<>();

    public Provider FindCustomProvider(QueryData queryData) throws Exception {
        String strQueryData = queryData.toString();
        for(Provider provider: providers) {
            if (provider.MatchQuery(queryData)) {
                if(!foundProviders.contains(strQueryData)) {
                    // So the message is displayed once only.
                    foundProviders.add(strQueryData);
                    logger.debug("Found provider for " + strQueryData);
                }
                return provider;
            }
        }
        if(!foundProviders.contains(strQueryData)) {
            // So the message is displayed once only.
            foundProviders.add(strQueryData);
            logger.debug("No provider found for " + strQueryData);
        }
        return null;
    }

    public ArrayList<Row> SelectVariablesFromWhere(QueryData queryData, boolean withCustom) throws Exception {
        if(withCustom) {
            Provider provider = FindCustomProvider(queryData);
            if(provider != null) {
                queryData.classProvider = provider.getClass();
                return provider.EffectiveSelect(queryData);
            }
        }
        queryData.classProvider = wmiSelecter.getClass();
        return wmiSelecter.EffectiveSelect(queryData);
    }

    public ArrayList<Row> SelectVariablesFromWhere(String className, String variable, Map<String, String> columns, List<QueryData.WhereEquality> wheres) throws Exception {
        return SelectVariablesFromWhere(new QueryData(className, variable, false,columns, wheres), true);
    }

    public ArrayList<Row> SelectVariablesFromWhere(String className, String variable, Map<String, String> columns) throws Exception {
        return SelectVariablesFromWhere(className, variable, columns, null);
    }

    ObjectGetter[] objectGetters = {
        new ObjectGetter_Cim_DataFile_Name(),
        new ObjectGetter_Win32_Process_Handle()
    };

    public ObjectGetter FindCustomGetter(QueryData queryData) throws Exception {
        for(ObjectGetter getter: objectGetters) {
            if (getter.MatchGet(queryData)) {
                return getter;
            }
        }
        return null;
    }

    Row GetObjectFromPath(String objectPath, QueryData queryData, boolean withCustom) throws Exception {
        if(withCustom) {
            ObjectGetter objectGetter = FindCustomGetter(queryData);
            if(objectGetter != null) {
                queryData.classGetter = objectGetter.getClass();
                return objectGetter.GetSingleObject(objectPath, queryData);
            }
        }

        queryData.classGetter = wmiSelecter.getClass();
        return wmiSelecter.GetSingleObject(objectPath, queryData);
    }
}

/*
Other features to add:

Ghidra
https://github.com/NationalSecurityAgency/ghidra/blob/master/Ghidra/Features/Base/src/main/java/ghidra/app/util/bin/format/pe/PortableExecutable.java
https://reverseengineering.stackexchange.com/questions/21207/use-ghidra-decompiler-with-command-line
https://static.grumpycoder.net/pixel/support/analyzeHeadlessREADME.html
 */