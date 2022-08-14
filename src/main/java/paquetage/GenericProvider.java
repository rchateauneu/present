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

abstract class BaseSelecter {
    public abstract boolean MatchProvider(QueryData queryData);

    // This assumes that all needed columns can be calculated.
    public abstract ArrayList<GenericProvider.Row> EffectiveSelect(QueryData queryData) throws Exception;

    // TODO: Estimate cost.
}

// TODO: Test separately these providers.

class BaseSelecter_CIM_DataFile_Name extends BaseSelecter {
    public boolean MatchProvider(QueryData queryData)
    {
        // In this selecter, the column "Name" must be provided.
        return queryData.CompatibleQuery(
                "CIM_DataFile",
                Set.of("Name"),
                BaseGetter_CIM_DataFile_Name.columnsMap.keySet());
    }

    /** This selects attributes of files whose name is given. This will return one file only.
     *
     * @param queryData
     * @return
     * @throws Exception
     */
    public ArrayList<GenericProvider.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericProvider.Row> result = new ArrayList<>();
        String fileName = queryData.GetWhereValue("Name");
        String pathFile = ObjectPath.BuildPathWbem("CIM_DataFile", Map.of("Name", fileName));
        GenericProvider.Row singleRow = new GenericProvider.Row();

        BaseGetter_CIM_DataFile_Name.FillRowFromQueryAndFilename(singleRow, queryData, fileName);

        // Add the main variable anyway.
        singleRow.PutNode(queryData.mainVariable, pathFile);

        result.add(singleRow);
        return result;
    }
}

class BaseSelecter_CIM_DirectoryContainsFile_PartComponent extends BaseSelecter {
    public boolean MatchProvider(QueryData queryData) {
        return queryData.CompatibleQuery(
                "CIM_DirectoryContainsFile",
                Set.of("PartComponent"),
                Set.of("GroupComponent"));
    }
    public ArrayList<GenericProvider.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericProvider.Row> result = new ArrayList<>();
        String valuePartComponent = queryData.GetWhereValue("PartComponent");
        Map<String, String> properties = ObjectPath.ParseWbemPath(valuePartComponent);
        String filePath = properties.get("Name");
        File file = new File(filePath);
        String parentPath = file.getAbsoluteFile().getParent();
        String pathDirectory = ObjectPath.BuildPathWbem("Win32_Directory", Map.of("Name", parentPath));

        GenericProvider.Row singleRow = new GenericProvider.Row();
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

class BaseSelecter_CIM_DirectoryContainsFile_GroupComponent extends BaseSelecter {
    public boolean MatchProvider(QueryData queryData) {
        return queryData.CompatibleQuery("CIM_DirectoryContainsFile",
                Set.of("GroupComponent"),
                Set.of("PartComponent"));
    }
    public ArrayList<GenericProvider.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericProvider.Row> result = new ArrayList<>();
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

            GenericProvider.Row singleRow = new GenericProvider.Row();

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
class BaseSelecter_CIM_ProcessExecutable_Antecedent extends BaseSelecter {
    static ProcessModules processModules = new ProcessModules();

    public boolean MatchProvider(QueryData queryData) {
        return queryData.CompatibleQuery(
                "CIM_ProcessExecutable",
                Set.of("Antecedent"),
                Set.of("Dependent"));
    }
    public ArrayList<GenericProvider.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericProvider.Row> result = new ArrayList<>();
        String valueAntecedent = queryData.GetWhereValue("Antecedent");
        Map<String, String> properties = ObjectPath.ParseWbemPath(valueAntecedent);
        String filePath = properties.get("Name");
        List<String> listPids = processModules.GetFromModule(filePath);

        String variableName = queryData.ColumnToVariable("Dependent");
        for(String onePid : listPids) {
            GenericProvider.Row singleRow = new GenericProvider.Row();
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
class BaseSelecter_CIM_ProcessExecutable_Dependent extends BaseSelecter {
    final static private Logger logger = Logger.getLogger(BaseSelecter_CIM_ProcessExecutable_Dependent.class);
    static ProcessModules processModules = new ProcessModules();

    public boolean MatchProvider(QueryData queryData) {
        return queryData.CompatibleQuery(
                "CIM_ProcessExecutable",
                Set.of("Dependent"),
                Set.of("Antecedent"));
    }
    public ArrayList<GenericProvider.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericProvider.Row> result = new ArrayList<>();
        String valueDependent = queryData.GetWhereValue("Dependent");
        Map<String, String> properties = ObjectPath.ParseWbemPath(valueDependent);
        String pidStr = properties.get("Handle");
        List<String> listModules = processModules.GetFromPid(pidStr);

        String variableName = queryData.ColumnToVariable("Antecedent");
        for(String oneFile : listModules) {
            GenericProvider.Row singleRow = new GenericProvider.Row();
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

abstract class BaseGetter {
    public abstract boolean MatchGetter(QueryData queryData);

    // This assumes that all needed columns can be calculated.
    public abstract GenericProvider.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception;

    // TODO: Cost estimation.
}

class BaseGetter_CIM_DataFile_Name extends BaseGetter {
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

    final static private Logger logger = Logger.getLogger(BaseGetter_CIM_DataFile_Name.class);

    // Get-WmiObject -Query 'select Drive from CIM_DataFile where Name="C:\\WINDOWS\\SYSTEM32\\ntdll.dll"'
    public boolean MatchGetter(QueryData queryData) {
        return queryData.ColumnsSubsetOf("CIM_DataFile", columnsMap.keySet());
    }

    static GenericProvider.Row.ValueTypePair FileToDrive(String fileName) {
        Path p = Paths.get(fileName);
        String driveStrRaw = p.getRoot().toString();
        return GenericProvider.Row.ValueTypePair.Factory(driveStrRaw.toLowerCase().substring(0, driveStrRaw.length()-1));
    }

    static GenericProvider.Row.ValueTypePair FileToName(String fileName) {
        Path p = Paths.get(fileName);
        String fileNameShort = p.getFileName().toString();
        return GenericProvider.Row.ValueTypePair.Factory(fileNameShort.substring(0, fileNameShort.lastIndexOf(".")));
    }

    static GenericProvider.Row.ValueTypePair FileToSize(String fileName) {
        File f = new File(fileName);
        long fileSize = f.length();
        logger.debug("fileName=" + fileName + " size=" + fileSize);
        return GenericProvider.Row.ValueTypePair.Factory(fileSize);
    }

    static GenericProvider.Row.ValueTypePair FileToPath(String fileName) {
        Path p = Paths.get(fileName);
        return GenericProvider.Row.ValueTypePair.Factory(p.getParent().toString().toLowerCase().substring(2) + "\\");
    }

    static Map<String, Function<String, GenericProvider.Row.ValueTypePair>> columnsMap = Map.of(
            "Caption", (String fileName) -> GenericProvider.Row.ValueTypePair.Factory(fileName),
            "Drive", (String fileName) -> FileToDrive(fileName),
            "FileName", (String fileName) -> FileToName(fileName),
            "FileSize", (String fileName) -> FileToSize(fileName),
            "Name", (String fileName) -> GenericProvider.Row.ValueTypePair.Factory(fileName),
            "Path", (String fileName) -> FileToPath(fileName)
            // "FileType", (String fileName) -> "Application Extension",
            );

    public static void FillRowFromQueryAndFilename(GenericProvider.Row singleRow, QueryData queryData, String fileName) {
        for(Map.Entry<String, String> qCol : queryData.queryColumns.entrySet()) {
            String columnKey = qCol.getKey();
            Function<String, GenericProvider.Row.ValueTypePair> lambda = columnsMap.get(columnKey);
            if(lambda == null)
            {
                throw new RuntimeException("No lambda for columnKey=" + columnKey + " fileName=" + fileName);
            }
            GenericProvider.Row.ValueTypePair variableValue = lambda.apply(fileName); // columnsMap.get)
            singleRow.PutValueType(qCol.getValue(), variableValue);
        }
    }

    public GenericProvider.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception {
        Map<String, String> properties = ObjectPath.ParseWbemPath(objectPath);
        String fileName = properties.get("Name");
        GenericProvider.Row singleRow = new GenericProvider.Row();
        FillRowFromQueryAndFilename(singleRow, queryData, fileName);

        // It must also the path of the variable of the object, because it may be used by an associator.
        String pathFile = ObjectPath.BuildPathWbem(
                "CIM_DataFile", Map.of(
                        "Name", fileName));
        singleRow.PutNode(queryData.mainVariable, pathFile);
        return singleRow;
    }
}

class BaseGetter_Win32_Process_Handle extends BaseGetter {
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
        public boolean MatchGetter(QueryData queryData) {
            return queryData.ColumnsSubsetOf("Win32_Process", columnsMap.keySet());
        }

        static ProcessModules processModules = new ProcessModules();

        static GenericProvider.Row.ValueTypePair ProcessToName(String processId) {
                List<String> listModules = processModules.GetFromPid(processId);
                String executablePath = listModules.get(0);
                Path p = Paths.get(executablePath);
                String fileNameShort = p.getFileName().toString();
                return GenericProvider.Row.ValueTypePair.Factory(fileNameShort);
        }

        /** Windows version and build number. */
        static GenericProvider.Row.ValueTypePair WindowsVersion(String processId) {
            Kernel32 kernel = Kernel32.INSTANCE;
            WinNT.OSVERSIONINFOEX vex = new WinNT.OSVERSIONINFOEX();
            String result;
            if (kernel.GetVersionEx(vex)) {
                result = MessageFormat.format("{0}.{1}.{2}",
                        vex.dwMajorVersion.toString(),
                        vex.dwMinorVersion.toString(),
                        vex.dwBuildNumber.toString());
            } else {
                result = "Cannot get  Windows version";
            }
            return GenericProvider.Row.ValueTypePair.Factory(result);
        }

        /** This contains the columns that this class can calculate, plus the lambda available. */
        static Map<String, Function<String, GenericProvider.Row.ValueTypePair>> columnsMap = Map.of(
                "Handle", (String processId) -> GenericProvider.Row.ValueTypePair.Factory(processId),
                "Name", (String processId) -> ProcessToName(processId),
                "ProcessId", (String processId) -> GenericProvider.Row.ValueTypePair.Factory(processId),
                "WindowsVersion", (String processId) -> WindowsVersion(processId)
        );

        public static void FillRowFromQueryAndPid(GenericProvider.Row singleRow, QueryData queryData, String processId) throws Exception {
            for(Map.Entry<String, String> qCol : queryData.queryColumns.entrySet()) {
                Function<String, GenericProvider.Row.ValueTypePair> lambda = columnsMap.get(qCol.getKey());
                if(lambda == null) {
                    throw new Exception("Cannot find lambda for " + qCol.getKey());
                }
                GenericProvider.Row.ValueTypePair variableValue = lambda.apply(processId);
                // These columns do not return a path, so a string is OK.
                singleRow.PutValueType(qCol.getValue(), variableValue);
            }
    }

    public GenericProvider.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception {
        Map<String, String> properties = ObjectPath.ParseWbemPath(objectPath);
        String processId = properties.get("Handle");
        GenericProvider.Row singleRow = new GenericProvider.Row();
        FillRowFromQueryAndPid(singleRow, queryData, processId);

        // It must also the path of the variable of the object, because it may be used by an associator.
        String pathFile = ObjectPath.BuildPathWbem(
                "Win32_Process", Map.of(
                        "Handle", processId));
        singleRow.PutNode(queryData.mainVariable, pathFile);
        return singleRow;
    }
}


public class GenericProvider {
    final static private Logger logger = Logger.getLogger(GenericProvider.class);

    /** This is a special value type for this software, to bridge data types between WMI/WBEM and RDF.
     * The most important feature is NODE_TYPE which models a WBEM path and an IRI.
     */
    public enum ValueType {
        STRING_TYPE,
        DATE_TYPE,
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
        public record ValueTypePair (String Value, ValueType Type) {
            public static ValueTypePair Factory(String value) {
                return new ValueTypePair(value, ValueType.STRING_TYPE);
            }

            public static ValueTypePair Factory(long value) {
                return new ValueTypePair(Long.toString(value), ValueType.INT_TYPE);
            }
        };

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
        public void PutLong(String key, String str) {
            Elements.put(key, new ValueTypePair(str, ValueType.INT_TYPE));
        }
        public void PutDate(String key, String str) {
            Elements.put(key, new ValueTypePair(str, ValueType.DATE_TYPE));
        }
        public void PutFloat(String key, String str) {
            Elements.put(key, new ValueTypePair(str, ValueType.FLOAT_TYPE));
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
                // TODO: If the value is a literal, it is formatted as in XML,
                // TODO: for example '"0"^^<http://www.w3.org/2001/XMLSchema#long>"
                ValueType valueType = bindingValue.isIRI() ? ValueType.NODE_TYPE : ValueType.STRING_TYPE;
                PutValueType(binding.getName(), new ValueTypePair(bindingValue.toString(), valueType));
            }
        }

        public String toString() {
            return Elements.toString();
        }
    }

    static private WmiProvider WmiProvider = new WmiProvider();

    static private BaseSelecter[] baseSelecters = {
        new BaseSelecter_CIM_DataFile_Name(),
        new BaseSelecter_CIM_DirectoryContainsFile_PartComponent(),
        new BaseSelecter_CIM_DirectoryContainsFile_GroupComponent(),
        new BaseSelecter_CIM_ProcessExecutable_Dependent(),
        new BaseSelecter_CIM_ProcessExecutable_Antecedent()
    };

    public GenericProvider()
    {}

    // This avoids to display the same message again and again.
    static private Set<String> foundSelecters = new HashSet<>();

    public static BaseSelecter FindCustomSelecter(QueryData queryData) {
        String strQueryData = queryData.toString();
        for(BaseSelecter baseSelecter : baseSelecters) {
            if (baseSelecter.MatchProvider(queryData)) {
                if(!foundSelecters.contains(strQueryData)) {
                    // So the message is displayed once only.
                    foundSelecters.add(strQueryData);
                    logger.debug("Found provider " + baseSelecter.getClass().getName() + " for " + strQueryData);
                }
                return baseSelecter;
            }
        }
        if(!foundSelecters.contains(strQueryData)) {
            // So the message is displayed once only.
            foundSelecters.add(strQueryData);
            logger.debug("No provider found for " + strQueryData);
        }
        return null;
    }

    static public BaseSelecter FindSelecter(QueryData queryData) throws Exception {
        BaseSelecter baseSelecter = FindCustomSelecter(queryData);
        return  (baseSelecter == null) ? wmiSelecter : baseSelecter;
    }

    static private WmiSelecter wmiSelecter = new WmiSelecter();

    public ArrayList<Row> SelectVariablesFromWhere(QueryData queryData, boolean withCustom) throws Exception {
        if(queryData.classBaseSelecter == null) {
            throw new RuntimeException("Provider is not set");
        }
        if(withCustom) {
            return queryData.classBaseSelecter.EffectiveSelect(queryData);
        } else {
            return wmiSelecter.EffectiveSelect(queryData);
        }
    }

    public ArrayList<Row> SelectVariablesFromWhere(String className, String variable, Map<String, String> columns, List<QueryData.WhereEquality> wheres) throws Exception {
        return SelectVariablesFromWhere(new QueryData(className, variable, false,columns, wheres), true);
    }

    public ArrayList<Row> SelectVariablesFromWhere(String className, String variable, Map<String, String> columns) throws Exception {
        return SelectVariablesFromWhere(className, variable, columns, null);
    }

    static private BaseGetter[] baseGetters = {
        new BaseGetter_CIM_DataFile_Name(),
        new BaseGetter_Win32_Process_Handle()
    };

    static private WmiGetter wmiGetter = new WmiGetter();

    static public BaseGetter FindCustomGetter(QueryData queryData) {
        logger.debug("Finding getter for:" + queryData.toString());
        for(BaseGetter getter: baseGetters) {
            if (getter.MatchGetter(queryData)) {
                logger.debug("Found provider" + getter.getClass().getName() + " for " + queryData);
                return getter;
            }
        }
        return null;
    }

    static public BaseGetter FindGetter(QueryData queryData) {
        BaseGetter getter = FindCustomGetter(queryData);
        return  (getter == null) ? wmiGetter : getter;
    }


    /**
     * @param objectPath
     * @param queryData
     * @param withCustom
     * @return
     * @throws Exception
     */
    Row GetObjectFromPath(String objectPath, QueryData queryData, boolean withCustom) throws Exception {
        if(queryData.classGetter == null) {
            throw new RuntimeException("Getter is not set");
        }
        if(withCustom) {
            return queryData.classGetter.GetSingleObject(objectPath, queryData);
        } else {
            return wmiGetter.GetSingleObject(objectPath, queryData);
        }
    }
}

/*
Other features to add:

Ghidra
https://github.com/NationalSecurityAgency/ghidra/blob/master/Ghidra/Features/Base/src/main/java/ghidra/app/util/bin/format/pe/PortableExecutable.java
https://reverseengineering.stackexchange.com/questions/21207/use-ghidra-decompiler-with-command-line
https://static.grumpycoder.net/pixel/support/analyzeHeadlessREADME.html
 */