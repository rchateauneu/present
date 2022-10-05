package paquetage;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import org.apache.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;

abstract class BaseSelecter {
    public abstract boolean MatchProvider(QueryData queryData);

    // This assumes that all needed columns can be calculated.
    public abstract Solution EffectiveSelect(QueryData queryData) throws Exception;

    // TODO: Estimate cost.
}

// TODO: Put these providers into different files ?.

class DummyClass {
    static public int MaxElements = 10;
}

/** This class is exclusively used for testing. */
class BaseSelecter_DummyClass_DummyKey extends BaseSelecter {
    public boolean MatchProvider(QueryData queryData)
    {
        return queryData.CompatibleQuery(
                "DummyClass",
                Set.of("DummyKey"),
                new HashSet<>());
    }

    /** This returns test data given an attribute.
     * It creates on the fly objects matching the query.
     *
     * @param queryData
     * @return
     * @throws Exception
     */
    public Solution EffectiveSelect(QueryData queryData) throws Exception {
        Solution result = new Solution();
        String dummyValue = queryData.GetWhereValue("DummyKey").toValueString();

        // The key must be an integer.
        long intDummyValue = Long.parseLong(dummyValue);

        if(intDummyValue >= 0 || intDummyValue < DummyClass.MaxElements) {
            // This assumes that this dummy class is in the namespace "Root/Cimv2"
            String pathDummy = ObjectPath.BuildCimv2PathWbem("DummyClass", Map.of("DummyKey", dummyValue));
            Solution.Row singleRow = new Solution.Row();

            singleRow.PutNode(queryData.mainVariable, pathDummy);

            result.add(singleRow);
        }
        return result;
    }
}

/** This class is exclusively used for testing. */
class BaseSelecter_DummyClass_All extends BaseSelecter {
    public boolean MatchProvider(QueryData queryData)
    {
        return queryData.CompatibleQuery(
                "DummyClass",
                Set.of(),
                Set.of("DummyKey"));
    }

    /** This returns test data given an attribute.
     * It creates 100 objects used for testing.
     *
     * @param queryData
     * @return
     * @throws Exception
     */
    public Solution EffectiveSelect(QueryData queryData) throws Exception {
        Solution result = new Solution();

        for(int key = 0; key < DummyClass.MaxElements; ++key) {
            String dummyKey = Long.toString(key);
            // This assumes that this dummy class is in the namespace "Root/Cimv2"
            String pathDummy = ObjectPath.BuildCimv2PathWbem("DummyClass", Map.of("DummyKey", dummyKey));
            Solution.Row singleRow = new Solution.Row();

            singleRow.PutNode(queryData.mainVariable, pathDummy);
            String variableName = queryData.ColumnToVariable("DummyKey");
            singleRow.PutString(variableName, dummyKey);

            result.add(singleRow);
        }
        return result;
    }
}

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
    public Solution EffectiveSelect(QueryData queryData) throws Exception {
        Solution result = new Solution();
        String fileName = queryData.GetWhereValue("Name").toValueString();
        String pathFile = ObjectPath.BuildCimv2PathWbem("CIM_DataFile", Map.of("Name", fileName));
        Solution.Row singleRow = new Solution.Row();

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
    public Solution EffectiveSelect(QueryData queryData) throws Exception {
        Solution result = new Solution();
        String valuePartComponent = queryData.GetWhereValue("PartComponent").toValueString();
        Map<String, String> properties = ObjectPath.ParseWbemPath(valuePartComponent);
        String filePath = properties.get("Name");
        File file = new File(filePath);
        String parentPath = file.getAbsoluteFile().getParent();
        String pathDirectory = ObjectPath.BuildCimv2PathWbem("Win32_Directory", Map.of("Name", parentPath));

        Solution.Row singleRow = new Solution.Row();
        String variableName = queryData.ColumnToVariable("GroupComponent");
        singleRow.PutNode(variableName, pathDirectory);

        // It must also the path of the associator row, even if it will probably not be used.
        String pathAssoc = ObjectPath.BuildCimv2PathWbem(
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
    public Solution EffectiveSelect(QueryData queryData) throws Exception {
        Solution result = new Solution();
        String valueGroupComponent = queryData.GetWhereValue("GroupComponent").toValueString();
        Map<String, String> properties = ObjectPath.ParseWbemPath(valueGroupComponent);
        String dirPath = properties.get("Name");
        String pathGroupComponent = ObjectPath.BuildCimv2PathWbem("Win32_Directory", Map.of("Name", dirPath));

        String variableName = queryData.ColumnToVariable("PartComponent");

        File path = new File(dirPath);

        File [] files = path.listFiles();
        for (File aFile : files){
            if (! aFile.isFile()){
                continue;
            }
            String fileName = aFile.getAbsoluteFile().toString();

            Solution.Row singleRow = new Solution.Row();

            String valuePartComponent = ObjectPath.BuildCimv2PathWbem(
                    "CIM_DataFile", Map.of(
                            "Name", fileName));
            singleRow.PutNode(variableName, valuePartComponent);

            // It must also the path of the associator row, even if it will probably not be used.
            String pathAssoc = ObjectPath.BuildCimv2PathWbem(
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
    public Solution EffectiveSelect(QueryData queryData) throws Exception {
        Solution result = new Solution();
        String valueAntecedent = queryData.GetWhereValue("Antecedent").toValueString();
        Map<String, String> properties = ObjectPath.ParseWbemPath(valueAntecedent);
        String filePath = properties.get("Name");
        List<String> listPids = processModules.GetFromModule(filePath);

        String variableName = queryData.ColumnToVariable("Dependent");
        for(String onePid : listPids) {
            Solution.Row singleRow = new Solution.Row();
            String pathDependent = ObjectPath.BuildCimv2PathWbem("Win32_Process", Map.of("Handle", onePid));
            singleRow.PutNode(variableName, pathDependent);

            // It must also the path of the associator row, even if it will probably not be used.
            String pathAssoc = ObjectPath.BuildCimv2PathWbem(
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
    public Solution EffectiveSelect(QueryData queryData) throws Exception {
        Solution result = new Solution();
        String valueDependent = queryData.GetWhereValue("Dependent").toValueString();
        Map<String, String> properties = ObjectPath.ParseWbemPath(valueDependent);
        String pidStr = properties.get("Handle");
        List<String> listModules = processModules.GetFromPid(pidStr);

        String variableName = queryData.ColumnToVariable("Antecedent");
        for(String oneFile : listModules) {
            Solution.Row singleRow = new Solution.Row();
            String pathAntecedent = ObjectPath.BuildCimv2PathWbem("CIM_DataFile", Map.of("Name", oneFile));
            singleRow.PutNode(variableName, pathAntecedent);

            // It must also the path of the associator row, even if it will probably not be used.
            String pathAssoc = ObjectPath.BuildCimv2PathWbem(
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
    public abstract Solution.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception;

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

    static ValueTypePair FileToDrive(String fileName) {
        Path p = Paths.get(fileName);
        String driveStrRaw = p.getRoot().toString();
        return ValueTypePair.Factory(driveStrRaw.toLowerCase().substring(0, driveStrRaw.length()-1));
    }

    static ValueTypePair FileToName(String fileName) {
        Path p = Paths.get(fileName);
        String fileNameShort = p.getFileName().toString();
        return ValueTypePair.Factory(fileNameShort.substring(0, fileNameShort.lastIndexOf(".")));
    }

    static ValueTypePair FileToSize(String fileName) {
        File f = new File(fileName);
        long fileSize = f.length();
        logger.debug("fileName=" + fileName + " size=" + fileSize);
        return ValueTypePair.Factory(fileSize);
    }

    static ValueTypePair FileToPath(String fileName) {
        Path p = Paths.get(fileName);
        return ValueTypePair.Factory(p.getParent().toString().toLowerCase().substring(2) + "\\");
    }

    static Map<String, Function<String, ValueTypePair>> columnsMap = Map.of(
            "Caption", (String fileName) -> ValueTypePair.Factory(fileName),
            "Drive", (String fileName) -> FileToDrive(fileName),
            "FileName", (String fileName) -> FileToName(fileName),
            "FileSize", (String fileName) -> FileToSize(fileName),
            "Name", (String fileName) -> ValueTypePair.Factory(fileName),
            "Path", (String fileName) -> FileToPath(fileName)
            // "FileType", (String fileName) -> "Application Extension",
            );

    public static void FillRowFromQueryAndFilename(Solution.Row singleRow, QueryData queryData, String fileName) {
        for(Map.Entry<String, String> qCol : queryData.queryColumns.entrySet()) {
            String columnKey = qCol.getKey();
            Function<String, ValueTypePair> lambda = columnsMap.get(columnKey);
            if(lambda == null)
            {
                throw new RuntimeException("No lambda for columnKey=" + columnKey + " fileName=" + fileName);
            }
            ValueTypePair variableValue = lambda.apply(fileName); // columnsMap.get)
            singleRow.PutValueType(qCol.getValue(), variableValue);
        }
    }

    public Solution.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception {
        Map<String, String> properties = ObjectPath.ParseWbemPath(objectPath);
        String fileName = properties.get("Name");
        Solution.Row singleRow = new Solution.Row();
        FillRowFromQueryAndFilename(singleRow, queryData, fileName);

        // It must also the path of the variable of the object, because it may be used by an associator.
        String pathFile = ObjectPath.BuildCimv2PathWbem(
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

        static ValueTypePair ProcessToName(String processId) {
                List<String> listModules = processModules.GetFromPid(processId);
                String executablePath = listModules.get(0);
                Path p = Paths.get(executablePath);
                String fileNameShort = p.getFileName().toString();
                return ValueTypePair.Factory(fileNameShort);
        }

        /** Windows version and build number. */
        static ValueTypePair WindowsVersion(String processId) {
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
            return ValueTypePair.Factory(result);
        }

        /** This contains the columns that this class can calculate, plus the lambda available. */
        static Map<String, Function<String, ValueTypePair>> columnsMap = Map.of(
                "Handle", (String processId) -> ValueTypePair.Factory(processId),
                "Name", (String processId) -> ProcessToName(processId),
                "ProcessId", (String processId) -> ValueTypePair.Factory(processId),
                "WindowsVersion", (String processId) -> WindowsVersion(processId)
        );

        public static void FillRowFromQueryAndPid(Solution.Row singleRow, QueryData queryData, String processId) throws Exception {
            for(Map.Entry<String, String> qCol : queryData.queryColumns.entrySet()) {
                Function<String, ValueTypePair> lambda = columnsMap.get(qCol.getKey());
                if(lambda == null) {
                    throw new Exception("Cannot find lambda for " + qCol.getKey());
                }
                ValueTypePair variableValue = lambda.apply(processId);
                // These columns do not return a path, so a string is OK.
                singleRow.PutValueType(qCol.getValue(), variableValue);
            }
    }

    public Solution.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception {
        Map<String, String> properties = ObjectPath.ParseWbemPath(objectPath);
        String processId = properties.get("Handle");
        if(processId == null) {
            throw new Exception("Null pid for objectPath=" + objectPath);
        }
        Solution.Row singleRow = new Solution.Row();
        FillRowFromQueryAndPid(singleRow, queryData, processId);

        // It must also the path of the variable of the object, because it may be used by an associator.
        String pathFile = ObjectPath.BuildCimv2PathWbem(
                "Win32_Process", Map.of(
                        "Handle", processId));
        singleRow.PutNode(queryData.mainVariable, pathFile);
        return singleRow;
    }
}


public class GenericProvider {
    final static private Logger logger = Logger.getLogger(GenericProvider.class);

    static private WmiProvider WmiProvider = new WmiProvider();

    static private BaseSelecter[] baseSelecters = {
        new BaseSelecter_DummyClass_DummyKey(),
        new BaseSelecter_DummyClass_All(),
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

    public Solution SelectVariablesFromWhere(QueryData queryData, boolean withCustom) throws Exception {
        if(queryData.classBaseSelecter == null) {
            throw new RuntimeException("Provider is not set");
        }
        /*
        * TODO: Results of a query could be stored if the "where" clause is identical for a given Sparql execution.
        * TODO: Also, if a QueryData is met with more restrictive "where" clauses, then reuse the result
        * TODO: with an extra filtering.
        */
        if(withCustom) {
            return queryData.classBaseSelecter.EffectiveSelect(queryData);
        } else {
            return wmiSelecter.EffectiveSelect(queryData);
        }
    }

    public Solution SelectVariablesFromWhere(
            String namespace,
            String className, String variable, Map<String, String> columns, List<QueryData.WhereEquality> wheres) throws Exception {
        return SelectVariablesFromWhere(new QueryData(namespace, className, variable, false,columns, wheres), true);
    }

    public Solution SelectVariablesFromWhere(
            String namespace,
            String className, String variable, Map<String, String> columns) throws Exception {
        return SelectVariablesFromWhere(namespace, className, variable, columns, null);
    }

    static private BaseGetter[] baseGetters = {
        new BaseGetter_CIM_DataFile_Name(),
        new BaseGetter_Win32_Process_Handle()
    };

    static private WmiGetter wmiGetter = new WmiGetter();

    static public BaseGetter FindCustomGetter(QueryData queryData) {
        logger.debug("Finding getter for:" + queryData.toString());

        // The columns in the "where" tests must be gettable from the object.
        // This is used when filtering getting an object.
        for(QueryData.WhereEquality whereTest : queryData.whereTests) {
            if(! queryData.queryColumns.containsKey(whereTest.predicate)) {
                logger.debug("Where column:" + whereTest.predicate + " missing from " + queryData.queryColumns.keySet());
            }
        }

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

    // Extra filtering if "where" test when getting an object.
    boolean ExtraFiltering(QueryData queryData, Solution.Row returnRow)
    {
        for(QueryData.WhereEquality oneWhere: queryData.whereTests) {
            logger.debug("    predicate=" + oneWhere.predicate + " value=" + oneWhere.value.toDisplayString() + " variableName=" + oneWhere.variableName);
            String variableName = queryData.ColumnToVariable(oneWhere.predicate);
            if(variableName == null) {
                throw new RuntimeException("Variable is null for column:" + oneWhere.predicate);
            }

            // Beware of performance waste if the same value is read twice from the object,
            // if the column is in the where  expression and also in the selected column.
            ValueTypePair vtp = returnRow.GetValueType(variableName);
            if (oneWhere.variableName != null) {
                throw new RuntimeException("Not handled yet. Should not be difficult if the variable is in the context:" + oneWhere.variableName);
            }
            //if (vtp.Type() != Solution.ValueType.STRING_TYPE) {
            //    throw new RuntimeException("Non-string handled yet:" + vtp.Type() + ". Should not be difficult.");
            //}
            if (!vtp.equals(oneWhere.value)) {
                logger.debug("Different column value:" + vtp.toDisplayString() + "!=" + oneWhere.value.toDisplayString());
                return false;
            }
        }
        return true;
    }

    /**
     * @param objectPath
     * @param queryData
     * @param withCustom If custom providers and getters can be used. This might not be the case when testing.
     * @return
     * @throws Exception
     */
    Solution.Row GetObjectFromPath(String objectPath, QueryData queryData, boolean withCustom) throws Exception {
        if(queryData.classGetter == null) {
            throw new RuntimeException("Getter is not set");
        }
        Solution.Row returnRow;
        if(withCustom) {
            returnRow = queryData.classGetter.GetSingleObject(objectPath, queryData);
        } else {
            returnRow = wmiGetter.GetSingleObject(objectPath, queryData);
        }
        // Now, apply the extra filtering if needed.
        if(ExtraFiltering(queryData, returnRow)) {
            return returnRow;
        } else {
            logger.debug("Filtered row");
            return null;
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