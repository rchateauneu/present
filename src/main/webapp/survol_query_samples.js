/*
This could be more flexible by generating dynamically this page.
*/
query_samples = [
    {
        category : "TCP/IP",
        title : "Processes ids of all TCP connection for Microsoft TCP/IP WMI v2 provider",
        query : `
        prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?owning_process
        where {
            ?tcp_connection rdf:type standard_cimv2:MSFT_NetTCPConnection .
            ?tcp_connection standard_cimv2:LocalAddress ?local_address .
            ?tcp_connection standard_cimv2:LocalPort ?local_port .
            ?tcp_connection standard_cimv2:RemoteAddress ?remote_address .
            ?tcp_connection standard_cimv2:RemotePort ?remote_port .
            ?tcp_connection standard_cimv2:OwningProcess ?owning_process .
        }
        `
    },
    {
        category : "TCP/IP",
        title : "Processes names of all TCP connection for Microsoft TCP/IP WMI v2 provider",
        query : `
            prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?process_name
            where {
                ?_1_tcp_connection standard_cimv2:MSFT_NetTCPConnection.OwningProcess ?owning_process .
                ?_2_process cimv2:Win32_Process.ProcessId ?owning_process .
                ?_2_process cimv2:Win32_Process.Name ?process_name .
            }
        `
    },
    {
         category : "TCP/IP",
         title : "Names of parent of processes with a TCP connection for Microsoft TCP/IP WMI v2 provider",
         query : `
            prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?process_name
            where {
                ?_1_tcp_connection standard_cimv2:MSFT_NetTCPConnection.OwningProcess ?owning_process .
                ?_2_process cimv2:Win32_Process.ProcessId ?owning_process .
                ?_2_process cimv2:Win32_Process.ParentProcessId ?parent_process_id .
                ?_3_process cimv2:Win32_Process.Handle ?parent_process_id .
                ?_3_process cimv2:Win32_Process.Name ?process_name .
            }
         `
     },
    {
         category : "TCP/IP",
         title : "Process pairs connected with a socket",
         query : `
            prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?owning_process1 ?owning_process2
            where {
                ?tcp_connection1 rdf:type standard_cimv2:MSFT_NetTCPConnection .
                ?tcp_connection1 standard_cimv2:LocalAddress ?local_address1 .
                ?tcp_connection1 standard_cimv2:LocalPort ?local_port1 .
                ?tcp_connection1 standard_cimv2:RemoteAddress ?remote_address1 .
                ?tcp_connection1 standard_cimv2:RemotePort ?remote_port1 .
                ?tcp_connection1 standard_cimv2:OwningProcess ?owning_process1 .
                ?tcp_connection2 rdf:type standard_cimv2:MSFT_NetTCPConnection .
                ?tcp_connection2 standard_cimv2:LocalAddress ?remote_address1 .
                ?tcp_connection2 standard_cimv2:LocalPort ?remote_port1 .
                ?tcp_connection2 standard_cimv2:RemoteAddress ?local_address1 .
                ?tcp_connection2 standard_cimv2:RemotePort ?local_port1 .
                ?tcp_connection2 standard_cimv2:OwningProcess ?owning_process2 .
            }
         `
     },
    {
        category : "TCP/IP",
        title : "Port numbers used by services and service name",
        query : `
            prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?local_port ?service_name
            where {
                ?_2_tcp_connection standard_cimv2:MSFT_NetTCPConnection.LocalPort ?local_port .
                ?_2_tcp_connection standard_cimv2:MSFT_NetTCPConnection.OwningProcess ?owning_process .
                ?_1_service cimv2:Win32_Service.DisplayName ?service_name .
                ?_1_service cimv2:Win32_Service.ProcessId ?owning_process .
            }
        `
    },
    {
        category : "TCP/IP",
        title : "Names of local processes connected together with a socket",
        query : `
            prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?process_name1 ?process_name2
            where {
                ?_1_tcp_connection rdf:type standard_cimv2:MSFT_NetTCPConnection .
                ?_1_tcp_connection standard_cimv2:LocalAddress ?local_address1 .
                ?_1_tcp_connection standard_cimv2:LocalPort ?local_port1 .
                ?_1_tcp_connection standard_cimv2:RemoteAddress ?remote_address1 .
                ?_1_tcp_connection standard_cimv2:RemotePort ?remote_port1 .
                ?_1_tcp_connection standard_cimv2:OwningProcess ?owning_process1 .
                ?_2_tcp_connection rdf:type standard_cimv2:MSFT_NetTCPConnection .
                ?_2_tcp_connection standard_cimv2:LocalAddress ?remote_address1 .
                ?_2_tcp_connection standard_cimv2:LocalPort ?remote_port1 .
                ?_2_tcp_connection standard_cimv2:RemoteAddress ?local_address1 .
                ?_2_tcp_connection standard_cimv2:RemotePort ?local_port1 .
                ?_2_tcp_connection standard_cimv2:OwningProcess ?owning_process2 .
                ?_3_process cimv2:Win32_Process.ProcessId ?owning_process1 .
                ?_3_process cimv2:Win32_Process.Name ?process_name1 .
                ?_4_process cimv2:Win32_Process.ProcessId ?owning_process2 .
                ?_4_process cimv2:Win32_Process.Name ?process_name2 .
            }
        `
    },
    {
        category : "Metadata",
        title : "Read-only boolean members of class Win32_Directory",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name ?dir_archive ?dir_compressed ?dir_encrypted ?dir_hidden ?dir_readable ?dir_system ?dir_writeable
            where
            {
                ?_1_dir cimv2:Win32_Directory.Name "C:\\\\WINDOWS" .
                ?_2_assoc_dir cimv2:Win32_SubDirectory.GroupComponent ?_1_dir .
                ?_2_assoc_dir cimv2:Win32_SubDirectory.PartComponent ?_3_subdir .
                ?_3_subdir cimv2:Win32_Directory.FileName ?dir_name .
                ?_3_subdir cimv2:Win32_Directory.Archive ?dir_archive .
                ?_3_subdir cimv2:Win32_Directory.Compressed ?dir_compressed .
                ?_3_subdir cimv2:Win32_Directory.Encrypted ?dir_encrypted .
                ?_3_subdir cimv2:Win32_Directory.Hidden ?dir_hidden .
                ?_3_subdir cimv2:Win32_Directory.Readable ?dir_readable .
                ?_3_subdir cimv2:Win32_Directory.System ?dir_system .
                ?_3_subdir cimv2:Win32_Directory.Writeable ?dir_writeable .
            }
        `
    },
    {
        category : "Metadata",
        title : "List of classes",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?class_label
            where {
                ?class rdf:type rdfs:Class .
                ?class rdfs:label ?class_label .
            }
        `
    },
    {
        category : "Metadata",
        title : "Attributes of Win32_Process",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?property_label
            where {
                ?property rdfs:domain cimv2:Win32_Process .
                ?property rdfs:label ?property_label .
            }
            `
        },
    {
        category : "Processes",
        title : "Caption of the process running the service 'Windows Search'",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?caption1 ?caption2
            where {
                ?_1_service cimv2:Win32_Service.DisplayName "Windows Search" .
                ?_1_service cimv2:Win32_Service.Caption ?caption1 .
                ?_1_service cimv2:Win32_Service.ProcessId ?process_id .
                ?_2_process cimv2:Win32_Process.ProcessId ?process_id .
                ?_2_process cimv2:Win32_Process.Caption ?caption2 .
            }
        `
    },
    {
        category : "Processes",
        title : "Antecedents of the process running the service 'Windows Search'",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?display_name ?dependency_type
            where {
                ?_1_service cimv2:Win32_Service.DisplayName "Windows Search" .
                ?_2_assoc cimv2:Win32_DependentService.Dependent ?_1_service .
                ?_2_assoc cimv2:Win32_DependentService.Antecedent ?_3_service .
                ?_2_assoc cimv2:Win32_DependentService.TypeOfDependency ?dependency_type .
                ?_3_service cimv2:Win32_Service.DisplayName ?display_name .
            }
        `
    },
    {
        category : "Processes",
        title : "Parent of the process running the service 'Windows Search'",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?parent_caption
            where {
                ?_1_service cimv2:Win32_Service.DisplayName "Windows Search" .
                ?_1_service cimv2:Win32_Service.ProcessId ?process_id .
                ?_2_process cimv2:Win32_Process.ProcessId ?process_id .
                ?_2_process cimv2:Win32_Process.ParentProcessId ?parent_process_id .
                ?_3_process cimv2:Win32_Process.ProcessId ?parent_process_id .
                ?_3_process cimv2:Win32_Process.Caption ?parent_caption .
            }
        `
    },
    {
        category : "Processes",
        title : "Oldest running process",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select (MIN(?creation_date) as ?min_creation_date)
            where {
                ?my_process cimv2:Win32_Process.CreationDate ?creation_date .
            } #group by ?my_process
        `
    },
    {
        category : "Processes",
        title : "Predicates of class Win32_Process",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?predicate
            where {
              ?predicate rdfs:domain cimv2:Win32_Process .
            }
        `
    },
    {
        category : "Processes",
        title : "Associators of class Win32_Process",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?class
            where {
              ?class rdfs:range cimv2:Win32_Process .
            }
        `
    },
    {
        category : "Processes",
        title : "Processes creation dates",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?caption ?credate
            where {
              ?process rdf:type cimv2:Win32_Process .
              ?process cimv2:Win32_Process.Caption ?caption .
              ?process cimv2:Win32_Process.CreationDate ?credate .
            }
        `
    },
    {
        category : "Accounts",
        title : "Selects all Win32_UserAccount",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?name ?domain ?description
            where {
                ?user rdf:type cimv2:Win32_UserAccount .
                ?user cimv2:Name ?name .
                ?user cimv2:Domain ?domain .
                ?user cimv2:Win32_UserAccount.Description ?description .
           }
        `
    },
    {
        category : "Volumes and directories",
        title : "Volume of a given directory",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?device_id
            where {
                ?my3_volume cimv2:DriveLetter ?my_drive .
                ?my3_volume cimv2:DeviceID ?device_id .
                ?my3_volume rdf:type cimv2:Win32_Volume .
                ?my0_dir rdf:type cimv2:Win32_Directory .
                ?my0_dir cimv2:Name "C:\\Program Files (x86)" .
                ?my0_dir cimv2:Drive ?my_drive .
            }
        `
    },
    {
        category : "Volumes and directories",
        title : "Mount point of a given directory",
        query : `
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my3_dir cimv2:Win32_Directory.Name ?my_dir_name .
                        ?my2_assoc cimv2:Win32_MountPoint.Volume ?my1_volume .
                        ?my2_assoc cimv2:Directory ?my3_dir .
                        ?my1_volume cimv2:Win32_Volume.DriveLetter ?my_drive .
                        ?my1_volume cimv2:DeviceID ?device_id .
                        ?my0_dir cimv2:Name "C:\\Program Files (x86)" .
                        ?my0_dir cimv2:Win32_Directory.Drive ?my_drive .
                    }
        `
    },
    {
        category : "Volumes and directories",
        title : "Top-level directories",
        query : `
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_directory
        where {
            ?my_directory rdf:type cimv2:Win32_Directory .
            ?my_directory cimv2:Name "C:" .
        }
        `
    },
    {
        category : "Volumes and directories",
        title : "Names of all directories (very slow)",
        query : `
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_name
        where {
            ?my_directory rdf:type cimv2:Win32_Directory .
            ?my_directory cimv2:Name ?my_name .
        }
        `
    },
    {
        category : "Volumes and directories",
        title : "Number of files in a directory",
        query : `
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (COUNT(?my2_file) as ?count_files)
                    where {
                        ?my1_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my2_file .
                        ?my1_assoc cimv2:GroupComponent ?my0_dir .
                        ?my0_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                    } group by ?my0_dir
        `
    },
    {
        category : "Volumes and directories",
        title : "Minimum, maximum and file sizes in a directory",
        query : `
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (MIN(?file_size) as ?size_min) (MAX(?file_size) as ?size_max) (xsd:long(SUM(?file_size)) as ?size_sum)
                    where {
                        ?my2_file cimv2:CIM_DataFile.FileSize ?file_size .
                        ?my1_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my2_file .
                        ?my1_assoc cimv2:GroupComponent ?my0_dir .
                        ?my0_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                    } group by ?my0_dir
        `
    },
    {
        category : "Volumes and directories",
        title : "Creation date of a Win32_Directory. Here, 'C:/Windows'",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?creation_date
            where {
                ?my_file cimv2:Win32_Directory.CreationDate ?creation_date .
                ?my_file cimv2:Win32_Directory.Name "C:\\\\Windows" .
            }
        `
    },
    {
        category : "Volumes and directories",
        title : "Test of protection mask",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_name
            where {
                ?my1_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                ?my2_assoc cimv2:GroupComponent ?my1_dir .
                ?my2_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my3_file .
                ?my3_file  cimv2:CIM_DataFile.Name ?my_name .
                ?my3_file  cimv2:CIM_DataFile.AccessMask "1179817"^^<http://www.w3.org/2001/XMLSchema#long> .
            }
        `
    },
    {
        category : "Volumes and directories",
        title : "Files in a directory, and their creation dates",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?file_name ?creation_date ?last_modified
            where {
                ?_1_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                ?_1_dir ^cimv2:CIM_DirectoryContainsFile.GroupComponent/cimv2:CIM_DirectoryContainsFile.PartComponent ?file .
                ?file cimv2:CIM_DataFile.Name ?file_name .
                ?file cimv2:CIM_DataFile.CreationDate ?creation_date .
                ?file cimv2:CIM_DataFile.LastModified ?last_modified .
            }
        `
    },
    {
        category : "Services",
        title : "Services dependent of the service 'Windows Search', at first level only",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?service_name ?path_name
            where {
                ?_1_service1 cimv2:Win32_Service.DisplayName "Windows Search" .
                ?_1_service1 ^cimv2:Win32_DependentService.Dependent/cimv2:Win32_DependentService.Antecedent ?zzzzz_2_service2 .
                ?zzzzz_2_service2 cimv2:Win32_Service.DisplayName ?service_name .
                ?zzzzz_2_service2 cimv2:Win32_Service.PathName ?path_name .
            }
        `
    },
    {
        category : "Services",
        title : "Dependency graph of Windows services.",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dependent ?antecedent
            where {
              ?assoc rdf:type cimv2:Win32_DependentService .
              ?assoc cimv2:Win32_DependentService.Dependent ?dependent .
              ?assoc cimv2:Win32_DependentService.Antecedent ?antecedent .
            }
        `
    },
    {
        category : "Services",
        title : "Services creation dates.",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?caption ?credate
            where {
              ?service rdf:type  cimv2:Win32_Service .
              ?service cimv2:Win32_Service.Caption  ?caption .
              ?service cimv2:Win32_Service.ProcessId ?pid .
              ?process rdf:type cimv2:Win32_Process .
              ?process cimv2:ProcessId ?pid .
              ?process cimv2:Win32_Process.CreationDate ?credate
            }
        `
    },
    {
        category : "Volumes and directories",
        title : "System directories, and checks that boolean constant values can be used in a query.",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where
            {
                ?_1_dir cimv2:Win32_Directory.Name "C:\\\\WINDOWS" .
                ?_2_assoc_dir cimv2:Win32_SubDirectory.GroupComponent ?_1_dir .
                ?_2_assoc_dir cimv2:Win32_SubDirectory.PartComponent ?_3_subdir .
                ?_3_subdir cimv2:Win32_Directory.FileName ?dir_name .
                ?_3_subdir cimv2:Win32_Directory.System "1"^^xsd:boolean .
            }
        `
    },
    {
        category : "Volumes and directories",
        title : "Union of system and non-system directories",
        query : `
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name where
            {
                {
                    select ?dir_name where
                    {
                        ?_1_dir cimv2:Win32_Directory.Name "C:\\\\WINDOWS" .
                        ?_2_assoc_dir cimv2:Win32_SubDirectory.GroupComponent ?_1_dir .
                        ?_2_assoc_dir cimv2:Win32_SubDirectory.PartComponent ?_3_subdir .
                        ?_3_subdir cimv2:Win32_Directory.FileName ?dir_name .
                        ?_3_subdir cimv2:Win32_Directory.System "0"^^xsd:boolean .
                    }
                }
                union
                {
                    select ?dir_name where
                    {
                        ?_1_dir cimv2:Win32_Directory.Name "C:\\\\WINDOWS" .
                        ?_2_assoc_dir cimv2:Win32_SubDirectory.GroupComponent ?_1_dir .
                        ?_2_assoc_dir cimv2:Win32_SubDirectory.PartComponent ?_3_subdir .
                        ?_3_subdir cimv2:Win32_Directory.FileName ?dir_name .
                        ?_3_subdir cimv2:Win32_Directory.System "1"^^xsd:boolean .
                    }
                }
            }
        `
    }
];
