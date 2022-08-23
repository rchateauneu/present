package paquetage;

import org.apache.commons.text.CaseUtils;
import org.junit.*;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** This tests Sparql selection from a repository containing the ontology plus the result of a WQL selection.
 * This is only for the namespace StandardCimv2.
 * */

public class RepositoryWrapperStandardCimv2Test {
    private RepositoryWrapper repositoryWrapper = null;

    @Before
    public void setUp() throws Exception {
        repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        // This checks that all processes are valid.
        // This test might fail if it is too slow.
        Set<Long> owningProcesses = PresentUtils.LongValuesSet(listRows,"owning_process");
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
     * ne faire la seconde qu'une seule fois. Soit on garde le resultat en cache, soit etc...
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Set<String> namesProcesses = PresentUtils.StringValuesSet(listRows,"process_name");
        System.out.println("namesProcesses=" + namesProcesses);
        // These processes open local socket connections.
        Assert.assertTrue(namesProcesses.contains("\"System Idle Process\""));
        Assert.assertTrue(namesProcesses.contains("\"lsass.exe\""));
        Assert.assertTrue(namesProcesses.contains("\"svchost.exe\""));
        Assert.assertTrue(namesProcesses.contains("\"services.exe\""));
        Assert.assertTrue(namesProcesses.contains("\"System\""));
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Set<String> namesProcesses = PresentUtils.StringValuesSet(listRows,"process_name");
        System.out.println("namesProcesses=" + namesProcesses);
        // These processes open local socket connections.
        Assert.assertTrue(namesProcesses.contains("\"System Idle Process\""));
        Assert.assertTrue(namesProcesses.contains("\"lsass.exe\""));
        Assert.assertTrue(namesProcesses.contains("\"svchost.exe\""));
        Assert.assertTrue(namesProcesses.contains("\"services.exe\""));
        Assert.assertTrue(namesProcesses.contains("\"System\""));
    }

    /**
     * Pairs of local processes connected together with a socket.
     * @throws Exception
     */
    @Test
    public void testMSFT_NetTCPConnection_ProcessPairs() throws Exception {
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        int countPresentProcess = 0;
        for(GenericProvider.Row oneRow: listRows) {
            Long pid1 = PresentUtils.XmlToLong(oneRow.GetStringValue("owning_process1"));
            // This test might fail if it is too slow.
            Optional<ProcessHandle> processHandle1 = ProcessHandle.of(pid1);
            Long pid2 = PresentUtils.XmlToLong(oneRow.GetStringValue("owning_process2"));
            Optional<ProcessHandle> processHandle2 = ProcessHandle.of(pid2);
            System.out.println("pid1=" + pid1 + " pid2=" + pid2);
            if(processHandle1.isPresent() && processHandle2.isPresent()) countPresentProcess++;
        }
        // At least a couple of processes should still be present.
        Assert.assertTrue(countPresentProcess > 3);
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Set<String> namesProcesses = PresentUtils.StringValuesSet(listRows,"process_name");
        System.out.println("namesProcesses=" + namesProcesses);
        Assert.assertTrue(namesProcesses.contains("\"svchost.exe\""));
        Assert.assertTrue(namesProcesses.contains("\"System\""));
    }

}
