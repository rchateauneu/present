package paquetage;

import org.junit.*;

import java.util.*;
import java.util.stream.Collectors;

/** This tests Sparql selection from a repository containing the ontology plus the result of a WQL selection.
 * This is only for the namespace StandardCimv2.
 *
 *   Consider using RepositoryResult<Namespace> getNamespaces(), et charger les ontologies en fonction de ca.
 * */

public class RepositoryWrapperStandardCimv2Test {
    private RepositoryWrapper repositoryWrapper = null;

    @Before
    public void setUp() throws Exception {
        // FIXME: Beware, only this ontology is loaded.
        //   Ajouter un test prenant en compte les deux ontologies.
        repositoryWrapper = new RepositoryWrapper("ROOT\\StandardCimv2");
    }

    //@Override
    @After
    public void tearDown() throws Exception {
        repositoryWrapper = null;
    }

    /**
     * Represents a TCP connection for the Microsoft TCP/IP WMI v2 provider.
     * @throws Exception
     */
    @Test
    public void testMSFT_NetTCPConnection() throws Exception {
        String sparqlQuery = """
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
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        // This checks that all processes are valid.
        // This test might fail if it is too slow because some processes might disappear,
        // so it just counts a reasonable minimum number of processes still present.
        Set<Long> owningProcesses = listRows.LongValuesSet("owning_process");
        int countPresentProcess = 0;
        for(Long onePid : owningProcesses) {
            System.out.println("Pid=" + onePid);
            Optional<ProcessHandle> processHandle = ProcessHandle.of(onePid);
            if(processHandle.isPresent()) countPresentProcess++;
        }
        // At least a couple of processes should still be present.
        Assert.assertTrue(countPresentProcess > 3);
    }

    /**
     * Select TCP connections of the Microsoft TCP/IP WMI v2 provider, and the process names.
     * It selects from two classes of different namespaces.
     * This classes order is faster than the other way around, because there are less sockets than processes,
     * or it is faster to select from processes than from sockets.
     *
     * TODO: Optimisation: Si deux requetes successives ne dependent pas l'une de l'autre,
     * TODO: ne faire la seconde qu'une seule fois. Soit on garde le resultat en cache, soit etc...
     *
     * TODO: The ontology of "ROOT/CIMV2" is not loaded.
     *
     *
     * @throws Exception
     */
    @Test
    public void testMSFT_NetTCPConnection_Win32_Process_Order1() throws Exception {
        String sparqlQuery = """
                    prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process_name
                    where {
                        ?_1_tcp_connection standard_cimv2:MSFT_NetTCPConnection.OwningProcess ?owning_process .
                        ?_2_process cimv2:Win32_Process.ProcessId ?owning_process .
                        ?_2_process cimv2:Win32_Process.Name ?process_name .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Set<String> namesProcesses = listRows.StringValuesSet("process_name");
        System.out.println("namesProcesses=" + namesProcesses);
        // These processes open local socket connections.
        Assert.assertTrue(namesProcesses.contains("System Idle Process"));
        Assert.assertTrue(namesProcesses.contains("lsass.exe"));
        Assert.assertTrue(namesProcesses.contains("svchost.exe"));
        Assert.assertTrue(namesProcesses.contains("services.exe"));
        Assert.assertTrue(namesProcesses.contains("System"));
    }

    /**
     * Select TCP connections of the Microsoft TCP/IP WMI v2 provider, and the process names.
     * It selects from two classes of different namespaces.
     * The order of evaluation is forced the other way around and it is slower.
     * @throws Exception
     */
    @Ignore ("Same as testMSFT_NetTCPConnection_Win32_Process_Order1 but slower")
    @Test
    public void testMSFT_NetTCPConnection_Win32_Process_Order2() throws Exception {
        String sparqlQuery = """
                    prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process_name
                    where {
                        ?_2_tcp_connection standard_cimv2:MSFT_NetTCPConnection.OwningProcess ?owning_process .
                        ?_1_process cimv2:Win32_Process.ProcessId ?owning_process .
                        ?_1_process cimv2:Win32_Process.Name ?process_name .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Set<String> namesProcesses = listRows.StringValuesSet("process_name");
        System.out.println("namesProcesses=" + namesProcesses);
        // These processes open local socket connections.
        Assert.assertTrue(namesProcesses.contains("System Idle Process"));
        Assert.assertTrue(namesProcesses.contains("lsass.exe"));
        Assert.assertTrue(namesProcesses.contains("svchost.exe"));
        Assert.assertTrue(namesProcesses.contains("services.exe"));
        Assert.assertTrue(namesProcesses.contains("System"));
    }

    /**
     * Select TCP connections of the Microsoft TCP/IP WMI v2 provider, and the names of the parent
     * processes of the processes holding these connections.
     *
     * @throws Exception
     */
    @Test
    public void testMSFT_NetTCPConnection_Win32_ParentProcessNames() throws Exception {
        String sparqlQuery = """
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
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Set<String> namesProcesses = listRows.StringValuesSet("process_name");
        System.out.println("namesProcesses=" + namesProcesses);
        // The parents of the processes which opened local socket connections.
        Assert.assertTrue(namesProcesses.contains("System Idle Process"));
        Assert.assertTrue(namesProcesses.contains("services.exe"));
        Assert.assertTrue(namesProcesses.contains("explorer.exe"));
        Assert.assertTrue(namesProcesses.contains("wininit.exe"));
    }

    /**
     * Pairs of local processes connected together with a socket.
     * @throws Exception
     */
    @Test
    public void testMSFT_NetTCPConnection_ProcessIdsPairs() throws Exception {
        String sparqlQuery = """
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
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        int countPresentProcess = 0;
        for(RdfSolution.Tuple oneRow : listRows) {
            Long pid1 = PresentUtils.XmlToLong(oneRow.GetAsLiteral("owning_process1"));
            // This test might fail if it is too slow.
            Optional<ProcessHandle> processHandle1 = ProcessHandle.of(pid1);
            Long pid2 = PresentUtils.XmlToLong(oneRow.GetAsLiteral("owning_process2"));
            Optional<ProcessHandle> processHandle2 = ProcessHandle.of(pid2);
            System.out.println("pid1=" + pid1 + " pid2=" + pid2);
            if(processHandle1.isPresent() && processHandle2.isPresent()) countPresentProcess++;
        }
        // At least a couple of processes should still be present.
        Assert.assertTrue(countPresentProcess > 3);
    }

    /**
     * Port numbers used by services and service name.
     * @throws Exception
     */
    @Test
    public void testMSFT_NetTCPConnection_ServicesPortNumbers() throws Exception {
        String sparqlQuery = """
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
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        HashMap<String, List<Long>> mapServiceToPort = new HashMap<>();
        for(RdfSolution.Tuple oneRow : listRows ) {
            Long portNumber = PresentUtils.XmlToLong(oneRow.GetAsLiteral("local_port"));
            String serviceName = oneRow.GetAsLiteral("service_name");
            System.out.println("portNumber=" + portNumber + " serviceName=" + serviceName);
            List<Long> servicePorts = mapServiceToPort.get(serviceName);
            if(servicePorts == null) {
                servicePorts = new ArrayList<>();
                mapServiceToPort.put(serviceName, servicePorts);
            }
            servicePorts.add(portNumber);
        }
        Assert.assertTrue(mapServiceToPort.get("\"RPC Endpoint Mapper\"").contains(Long.valueOf(135)));
        Assert.assertTrue(mapServiceToPort.get("\"Connected Devices Platform Service\"").contains(Long.valueOf(5040)));
        Assert.assertTrue(mapServiceToPort.get("\"Delivery Optimization\"").contains(Long.valueOf(7680)));
    }

    /**
     * Names of local processes connected together with a socket.
     * @throws Exception
     */
    @Test
    public void testMSFT_NetTCPConnection_ProcessNamesPairs() throws Exception {
        String sparqlQuery = """
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
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        /* Result example:
        name1="MediaEngineService.exe" name2="wfica32.exe"
        name1="java.exe" name2="idea64.exe"
        name1="idea64.exe" name2="java.exe"
        name1="idea64.exe" name2="idea64.exe"
        name1="firefox.exe" name2="firefox.exe"
        name1="Tomcat9.exe" name2="Tomcat9.exe"
        */
        Set<String> setProcessNamesPairs = new HashSet<>();
        for(RdfSolution.Tuple oneRow : listRows) {
            String name1 = oneRow.GetAsLiteral("process_name1");
            String name2 = oneRow.GetAsLiteral("process_name2");
            System.out.println("name1=" + name1 + " name2=" + name2);
            setProcessNamesPairs.add(name1 + " + " + name2);
        }
        System.out.println("setProcessNamesPairs=" + setProcessNamesPairs);
        Assert.assertTrue(setProcessNamesPairs.contains("\"java.exe\" + \"idea64.exe\""));
        //Assert.assertTrue(setProcessNamesPairs.contains("\"MediaEngineService.exe\" + \"wfica32.exe\""));
    }

    /**
     * Select from TCP connection for the Microsoft TCP/IP WMI v2 provider, and the process names.
     * It selects from two classes of different namespaces.
     * The order of evaluation is forced the other way around and it slower.
     * @throws Exception
     */
    @Test
    public void testMSFT_NetUDPEndpoint_Win32_Process() throws Exception {
        String sparqlQuery = """
                    prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process_name
                    where {
                        ?_1_tcp_connection standard_cimv2:MSFT_NetUDPEndpoint.OwningProcess ?owning_process .
                        ?_2_process cimv2:Win32_Process.ProcessId ?owning_process .
                        ?_2_process cimv2:Win32_Process.Name ?process_name .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Set<String> namesProcesses = listRows.StringValuesSet("process_name");
        System.out.println("namesProcesses=" + namesProcesses);
        Assert.assertTrue(namesProcesses.contains("svchost.exe"));
        Assert.assertTrue(namesProcesses.contains("System"));
    }

    /** Checks that the namespace is correct. */
    @Test
    public void testMSFT_NetIPAddress() throws Exception {
        String sparqlQuery = """
                    prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?net_ip_address ?interface_alias
                    where {
                        ?net_ip_address standard_cimv2:MSFT_NetIPAddress.InterfaceAlias ?interface_alias .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        // One of these usual interface aliases should be present.
        Set<String> usualAliases = Set.of("Loopback Pseudo-Interface 1", "Ethernet", "Local Area Connection", "WiFi", "Bluetooth Network Connection", "Local Area Connection* 1");
        Set<String> setAliases = listRows.StringValuesSet("interface_alias");
        Set intersectAlias = usualAliases.stream().filter(setAliases::contains).collect(Collectors.toSet());
        Assert.assertFalse(intersectAlias.isEmpty());

        Set<String> setIpAddresses = listRows.NodeValuesSet("net_ip_address");
        System.out.println("setIpAddresses=" + setIpAddresses);
        Assert.assertTrue(setIpAddresses.size() > 0);
        String prefixIri = WmiOntology.NamespaceUrlPrefix("ROOT\\StandardCimv2");
        System.out.println("prefixIri=" + prefixIri);
        for(String iriIpAddress: setIpAddresses) {
            System.out.println("iriIpAddress=" + iriIpAddress);
            Assert.assertTrue(iriIpAddress.startsWith(prefixIri));
        }
    }
}
