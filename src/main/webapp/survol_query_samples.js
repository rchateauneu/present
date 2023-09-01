//alert("Setting query_samples");
query_samples = [
    {
        category : "Directories",
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
        category : "Directories",
        title : "Names of all directories",
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
        category : "TCP/IP",
        title : "xxx",
        query : `
        `
    },
    {
        category : "TCP/IP",
        title : "xxx",
        query : `
        `
    },
    {
        category : "TCP/IP",
        title : "xxx",
        query : `
        `
    }
];
//module.exports = query_samples;