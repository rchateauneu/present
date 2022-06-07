package paquetage;

import java.io.File;
import java.util.*;

abstract class Provider {
    public abstract boolean MatchQuery(QueryData queryData);

    // This assumes that all needed columns can be calculated.
    public abstract ArrayList<MetaSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception;

    public ArrayList<MetaSelecter.Row> TrySelect(QueryData queryData) throws Exception
    {
        if( ! MatchQuery(queryData)) {
            return null;
        }
        return EffectiveSelect(queryData);
    }
}

// TODO: Test separately this provider.
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

        for( Map.Entry<String, String> column : queryData.queryColumns.entrySet()) {
            if(column.getKey().equals("FileName")) {
                singleRow.Elements.put(queryData.mainVariable, fileName);
            } else {
                throw new Exception("Not implemented yet");
            }
        }

        // Add the main variable anyway.
        singleRow.Elements.put(queryData.mainVariable, pathFile);

        result.add(singleRow);
        return result;
    }
}

// TODO: Test separately this provider.
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
        for( Map.Entry<String, String> column : queryData.queryColumns.entrySet()) {
            if(column.getKey().equals("GroupComponent")) {
                singleRow.Elements.put(column.getValue(), pathDirectory);
            }
        }

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

// TODO: Test separately this provider.
class Provider_CIM_ProcessExecutable_Dependent extends Provider {
    public boolean MatchQuery(QueryData queryData) {
        return false;
        //return queryData.CompatibleQuery("CIM_ProcessExecutable", Set.of("Dependent"));
    }
    public ArrayList<MetaSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<MetaSelecter.Row> result = new ArrayList<>();
        String valueDependent = queryData.GetWhereValue("Dependent");




        ProcessHandle.allProcesses().forEach(processHandle -> {
            System.out.println(processHandle.pid()+" "+processHandle.info());
        });
        long pid = 12345;
        Optional op = ProcessHandle.of(pid);
        ProcessHandle.Info in = ProcessHandle.of(pid).get().info();


        return result;
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

        public String toString() {
            return Elements.toString();
        }
    }

    WmiSelecter wmiSelecter = new WmiSelecter();

    public MetaSelecter()
    {}

    public ArrayList<Row> WqlSelect(QueryData queryData) throws Exception {
        /*
        Most costly queries according to Statistics:

        CIM_ProcessExecutable:Dependent:264.706 s 333
        TOTAL:264.706 s 333 calls 1 lines

        CIM_ProcessExecutable:Antecedent:106.719 s 120
        TOTAL:106.719 s 120 calls 1 lines

        CIM_DirectoryContainsFile:PartComponent:28.174 s 10376
        TOTAL:28.174 s 10376 calls 1 lines
        */
        if(1!= 1 + 1) {
            ArrayList<Row> result = null;

            result = new Provider_CIM_DataFile_Name().TrySelect(queryData);
            if (result != null) {
                return result;
            }
            result = new Provider_CIM_DirectoryContainsFile_PartComponent().TrySelect(queryData);
            if (result != null) {
                return result;
            }
            result = new Provider_CIM_ProcessExecutable_Dependent().TrySelect(queryData);
            if (result != null) {
                return result;
            }
        }
        return wmiSelecter.WqlSelectWMI(queryData);
    }

    public ArrayList<Row> WqlSelect(String className, String variable, Map<String, String> columns, List<QueryData.WhereEquality> wheres) throws Exception {
        return WqlSelect(new QueryData(className, variable, false,columns, wheres));
    }

    public ArrayList<Row> WqlSelect(String className, String variable, Map<String, String> columns) throws Exception {
        return WqlSelect(className, variable, columns, null);
    }

    void GetVariablesFromNodePath(String objectPath, QueryData queryData, Map<String, String> variablesContext) throws Exception {
        /*
        Most costly queries according to Statistics:

        \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\SYSTEM32\\HologramWorld.dll":Name:0.158 s 1
        TOTAL:55.696 s 4395 calls 4395 lines

        \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\Users\\rchat\\AppData\\Local\\Microsoft\\OneDrive\\22.099.0508.0001\\Qt5Gui.dll"::0.573 s 1
        \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\SYSTEM32\\ntdll.dll"::0.0 s 172
        TOTAL:17.614 s 10376 calls 1276 lines

        \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\SYSTEM32\\HologramWorld.dll":Name:0.158 s 1
        TOTAL:55.696 s 4395 calls 4395 lines

        */
        wmiSelecter.GetVariablesFromNodePath(objectPath, queryData, variablesContext);
    }
}
