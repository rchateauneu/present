package paquetage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

abstract class Provider {
    public abstract boolean MatchQuery(QueryData queryData);

    // This assumes that all needed columns can be calculated.
    public abstract ArrayList<MetaSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception;

    public ArrayList<MetaSelecter.Row> TrySelectFromWhere(QueryData queryData) throws Exception
    {
        if( ! MatchQuery(queryData)) {
            return null;
        }
        System.out.println("Found provider:" + this.getClass().toString());
        return EffectiveSelect(queryData);
    }

    // TODO: Estimate cost.
}

// TODO: Test separately these providers.

class Provider_CIM_DataFile_Name extends Provider {
    public boolean MatchQuery(QueryData queryData) {
        return queryData.CompatibleQuery("CIM_DataFile", Set.of("Name"));
    }
    public ArrayList<MetaSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<MetaSelecter.Row> result = new ArrayList<>();
        String fileName = queryData.GetWhereValue("Name");
        String pathFile = ObjectPath.BuildPathWbem("CIM_DataFile", Map.of("Name", fileName));

        MetaSelecter.Row singleRow = new MetaSelecter.Row();
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

        String variableCaption = queryData.ColumnToVariable("Caption");
        if(variableCaption != null)
            singleRow.Elements.put(variableCaption, fileName);
        String variableDrive = queryData.ColumnToVariable("Drive");
        if(variableDrive != null) {
            Path p = Paths.get(fileName);
            String driveStrRaw = p.getRoot().toString();
            String driveStr = driveStrRaw.toLowerCase().substring(0, driveStrRaw.length()-1);
            singleRow.Elements.put(variableDrive, driveStr);
        }
        String variableFileName = queryData.ColumnToVariable("FileName");
        if(variableFileName != null) {
            Path p = Paths.get(fileName);
            String fileNameShort = p.getFileName().toString();
            String fileNameNoExt = fileNameShort.substring(0, fileNameShort.lastIndexOf("."));
            singleRow.Elements.put(variableFileName, fileNameNoExt);
        }
        String variableFileSize = queryData.ColumnToVariable("FileSize");
        if(variableFileSize != null) {
            File f = new File(fileName);
            long fileSize = f.length();
            String fileSizeStr = Long.toString(fileSize);
            singleRow.Elements.put(variableFileSize, fileSizeStr);
        }
        // Application Extension
        if(false) {
            String variableFileType = queryData.ColumnToVariable("FileType");
            if (variableFileType != null)
                singleRow.Elements.put(variableFileType, fileName);
        }
        String variablePath = queryData.ColumnToVariable("Path");
        if(variablePath != null) {
            Path p = Paths.get(fileName);
            String pa = p.getParent().toString().toLowerCase().substring(2) + "\\";
            singleRow.Elements.put(variablePath, pa);
        }

        // Add the main variable anyway.
        singleRow.Elements.put(queryData.mainVariable, pathFile);

        result.add(singleRow);
        return result;
    }
}

class Provider_CIM_DirectoryContainsFile_PartComponent extends Provider {
    public boolean MatchQuery(QueryData queryData) {
        return queryData.CompatibleQuery("CIM_DirectoryContainsFile", Set.of("PartComponent"));
    }
    public ArrayList<MetaSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<MetaSelecter.Row> result = new ArrayList<>();
        String valuePartComponent = queryData.GetWhereValue("PartComponent");
        Map<String, String> properties = ObjectPath.ParseWbemPath(valuePartComponent);
        String filePath = properties.get("Name");
        File file = new File(filePath);
        String parentPath = file.getAbsoluteFile().getParent();
        String pathDirectory = ObjectPath.BuildPathWbem("Win32_Directory", Map.of("Name", parentPath));

        MetaSelecter.Row singleRow = new MetaSelecter.Row();
        String variableName = queryData.ColumnToVariable("GroupComponent");
        singleRow.Elements.put(variableName, pathDirectory);

        // It must also the path of the associator row, even if it will probably not be used.
        String pathAssoc = ObjectPath.BuildPathWbem(
                "CIM_DirectoryContainsFile", Map.of(
                        "PartComponent", valuePartComponent,
                        "GroupComponent", pathDirectory));
        singleRow.Elements.put(queryData.mainVariable, pathAssoc);

        result.add(singleRow);
        return result;
    }
}

class Provider_CIM_DirectoryContainsFile_GroupComponent extends Provider {
    public boolean MatchQuery(QueryData queryData) {
        return queryData.CompatibleQuery("CIM_DirectoryContainsFile", Set.of("GroupComponent"));
    }
    public ArrayList<MetaSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<MetaSelecter.Row> result = new ArrayList<>();
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

            MetaSelecter.Row singleRow = new MetaSelecter.Row();

            String valuePartComponent = ObjectPath.BuildPathWbem(
                    "CIM_DataFile", Map.of(
                            "Name", fileName));
            singleRow.Elements.put(variableName, valuePartComponent);

            // It must also the path of the associator row, even if it will probably not be used.
            String pathAssoc = ObjectPath.BuildPathWbem(
                    "CIM_DirectoryContainsFile", Map.of(
                            "PartComponent", valuePartComponent,
                            "GroupComponent", pathGroupComponent));
            singleRow.Elements.put(queryData.mainVariable, pathAssoc);

            result.add(singleRow);
        }

        return result;
    }
}

/** The input is a module, a filename. It returns processes using it.
 *
 */
class Provider_CIM_ProcessExecutable_Antecedent extends Provider {
    ProcessModules processModules = new ProcessModules();

    public boolean MatchQuery(QueryData queryData) {
        return queryData.CompatibleQuery("CIM_ProcessExecutable", Set.of("Antecedent"));
    }
    public ArrayList<MetaSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<MetaSelecter.Row> result = new ArrayList<>();
        String valueAntecedent = queryData.GetWhereValue("Antecedent");
        Map<String, String> properties = ObjectPath.ParseWbemPath(valueAntecedent);
        String filePath = properties.get("Name");
        List<String> listPids = processModules.GetFromModule(filePath);

        String variableName = queryData.ColumnToVariable("Dependent");
        for(String onePid : listPids) {
            MetaSelecter.Row singleRow = new MetaSelecter.Row();
            String pathDependent = ObjectPath.BuildPathWbem("Win32_Process", Map.of("Handle", onePid));
            singleRow.Elements.put(variableName, pathDependent);

            // It must also the path of the associator row, even if it will probably not be used.
            String pathAssoc = ObjectPath.BuildPathWbem(
                    "CIM_DirectoryContainsFile", Map.of(
                            "Dependent", pathDependent,
                            "Antecedent", valueAntecedent));
            singleRow.Elements.put(queryData.mainVariable, pathAssoc);

            result.add(singleRow);
        }

        return result;
    }
}

/** The input is a process and it returns its executable and libraries.
 *
 */
class Provider_CIM_ProcessExecutable_Dependent extends Provider {
    ProcessModules processModules = new ProcessModules();

    public boolean MatchQuery(QueryData queryData) {
        return queryData.CompatibleQuery("CIM_ProcessExecutable", Set.of("Dependent"));
    }
    public ArrayList<MetaSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<MetaSelecter.Row> result = new ArrayList<>();
        String valueDependent = queryData.GetWhereValue("Dependent");
        Map<String, String> properties = ObjectPath.ParseWbemPath(valueDependent);
        String pidStr = properties.get("Handle");
        List<String> listModules = processModules.GetFromPid(pidStr);

        String variableName = queryData.ColumnToVariable("Antecedent");
        for(String oneFile : listModules) {
            MetaSelecter.Row singleRow = new MetaSelecter.Row();
            String pathAntecedent = ObjectPath.BuildPathWbem("CIM_DataFile", Map.of("Name", oneFile));
            singleRow.Elements.put(variableName, pathAntecedent);

            // It must also the path of the associator row, even if it will probably not be used.
            String pathAssoc = ObjectPath.BuildPathWbem(
                    "CIM_DirectoryContainsFile", Map.of(
                            "Dependent", valueDependent,
                            "Antecedent", pathAntecedent));
            singleRow.Elements.put(queryData.mainVariable, pathAssoc);

            result.add(singleRow);
        }

        return result;
    }
}

abstract class ObjectGetter {
    public abstract boolean MatchGet(QueryData queryData);

    // This assumes that all needed columns can be calculated.
    public abstract MetaSelecter.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception;

    public MetaSelecter.Row TryGetObject(String objectPath, QueryData queryData) throws Exception
    {
        if( ! MatchGet(queryData)) {
            return null;
        }
        System.out.println("Found provider:" + this.getClass().toString());
        return GetSingleObject(objectPath, queryData);
    }

    // TODO: Cost estimation.
}

class ObjectGetter_Cim_DataFile_Name extends ObjectGetter {
    public boolean MatchGet(QueryData queryData) {
        return queryData.ColumnsSubsetOf("CIM_DataFile", Set.of("Name"));
    }
    public MetaSelecter.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception {
        Map<String, String> properties = ObjectPath.ParseWbemPath(objectPath);
        String fileName = properties.get("Name");
        MetaSelecter.Row singleRow = new MetaSelecter.Row();
        String variableName = queryData.ColumnToVariable("Name");
        if(variableName != null) {
            singleRow.Elements.put(variableName, fileName);
        }

        // It must also the path of the variable of the object, because it may be used by an associator.
        String pathFile = ObjectPath.BuildPathWbem(
                "CIM_DataFile", Map.of(
                        "Name", fileName));
        singleRow.Elements.put(queryData.mainVariable, pathFile);
        return singleRow;
    }
}

class ObjectGetter_Win32_Process_Handle extends ObjectGetter {
    public boolean MatchGet(QueryData queryData) {
        return queryData.ColumnsSubsetOf("Win32_Process", Set.of("Handle"));
    }
    public MetaSelecter.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception {
        Map<String, String> properties = ObjectPath.ParseWbemPath(objectPath);
        String fileName = properties.get("Handle");
        MetaSelecter.Row singleRow = new MetaSelecter.Row();
        String variableName = queryData.ColumnToVariable("Handle");
        if(variableName != null) {
            singleRow.Elements.put(variableName, fileName);
        }

        // It must also the path of the variable of the object, because it may be used by an associator.
        String pathFile = ObjectPath.BuildPathWbem(
                "Win32_Process", Map.of(
                        "Handle", fileName));
        singleRow.Elements.put(queryData.mainVariable, pathFile);
        return singleRow;
    }
}


public class MetaSelecter {
    /**
     * This is a row returned by a WMI select query.
     * For simplicity, each row contains all column names.
     */
    public static class Row {
        Map<String, String> Elements;

        public Row() {
            Elements = new HashMap<String, String>();
        }

        /**
         * This is for testing. An input row is inserted, then triples are created using an existing list of patterns.
         * @param elements
         */
        public Row(Map<String, String> elements) {
            Elements = elements;
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

    public MetaSelecter()
    {}

    public ArrayList<Row> SelectVariablesFromWhere(QueryData queryData, boolean withCustom) throws Exception {
        if(withCustom) {
            // TODO : Vary tests by enabling/disabling providers.
            ArrayList<Row> rowsArray = null;
            for(Provider provider: providers) {
                rowsArray = provider.TrySelectFromWhere(queryData);
                if (rowsArray != null) {
                    return rowsArray;
                }
            }
        }
        return wmiSelecter.TrySelectFromWhere(queryData);
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

    Row GetObjectFromPath(String objectPath, QueryData queryData, boolean withCustom) throws Exception {
        if(withCustom) {
            // TODO : Vary tests by enabling/disabling providers.
            Row singleRow = null;
            for(ObjectGetter objectGetter: objectGetters) {
                singleRow = objectGetter.TryGetObject(objectPath, queryData);
                if (singleRow != null) {
                    return singleRow;
                }
            }
        }

        return wmiSelecter.TryGetObject(objectPath, queryData);
    }
}

/*
Other features to add:

Ghidra
https://github.com/NationalSecurityAgency/ghidra/blob/master/Ghidra/Features/Base/src/main/java/ghidra/app/util/bin/format/pe/PortableExecutable.java
https://reverseengineering.stackexchange.com/questions/21207/use-ghidra-decompiler-with-command-line
https://static.grumpycoder.net/pixel/support/analyzeHeadlessREADME.html
 */