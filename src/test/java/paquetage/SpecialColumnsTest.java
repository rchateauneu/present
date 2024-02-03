package paquetage;

import org.junit.*;

import java.util.*;

/** Some columns of WMI classes must be specially processed. */
public class SpecialColumnsTest {
    static long currentPid = ProcessHandle.current().pid();
    static String currentPidStr = String.valueOf(currentPid);

    private RepositoryWrapper repositoryWrapper = null;

    @Before
    public void setUp() throws Exception {
        repositoryWrapper = new RepositoryWrapper("ROOT\\CIMV2");
    }

    @After
    public void tearDown() throws Exception {
        repositoryWrapper = null;
    }

    /** This gets all processes with the current machine name and checks that the current one is found. */
    @Test
    public void testSelect_Win32_Process_PSComputerName_Where() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?handle
                    where {
                        ?process cimv2:Win32_Process.PSComputerName "%s" .
                        ?process cimv2:Win32_Process.Handle ?handle .
                    }
                """, PresentUtils.computerName);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        Set<String> setHandles = listRows.stringValuesSet("handle");
        Assert.assertTrue(setHandles.contains(currentPidStr));
    }

    /** This gets machines of all processes. The intention of this test is to check how the column
     * "PSComputerName" is processed. */
    @Test
    public void testSelect_Win32_Process_PSComputerName_Select() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?machine
                    where {
                        ?process cimv2:Win32_Process.PSComputerName ?machine .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        Set<String> setMachines = listRows.stringValuesSet("machine");
        Assert.assertEquals(Set.of(PresentUtils.computerName), setMachines);
    }

    /** This must throw an exception because the machine is given and is not a variable.
     *
     * @throws Exception
     */
    @Test (expected = RuntimeException.class)
    public void testSelect_Win32_Process_PSComputerName_Select_Known() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?machine
                    where {
                        ?process cimv2:Win32_Process.PSComputerName "AnyOtherMachine" .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
    }

    /*
    Win32_Process.GetOwner() method to get the owner and domain.
            uint32 GetOwner(
              [out] string User,   # Returns the user name of the owner of this process.
              [out] string Domain  # Returns the domain name under which this process is running.
            );                     # Returns zero (0) to indicate success.

        https://blogs.msmvps.com/richardsiddaway/2014/03/23/discovering-cim-wmi-methods-and-parameters/
        It is possible to get the parameter names of the method of a class with these Powershell commands:
        $class = Get-CimClass -ClassName Win32_Process
        $class.CimClassMethods
        $class.CimClassMethods["Create"].Parameters

            Name   CimType Qualifiers                ReferenceClassName
            ----   ------- ----------                ------------------
            Domain  String {ID, MappingStrings, Out}
            User    String {ID, MappingStrings, Out}

        The syntax to call a method could be, for this example:

        ?process cimv2:Wim32_Process.GetOwner ?method_call_result
        ?method_call_result cimv2:Wim32_Process.GetOwner.Domain ?process_domain
        ?method_call_result cimv2:Wim32_Process.GetOwner.User ?process_user

        Some restrictions: The variables ?method_call_result, ?process_domain and ?process_user
        must not have a value.
        And there are of course security issues for non-getter and potentially destructive methods.
    */
    @Ignore("Not designed nor implemented yet")
    @Test
    public void testSelect_Win32_Process_GetOwner() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?owner
                    where {
                        ?process cimv2:Win32_Process.GetOwner ?owner .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        Set<String> setMachines = listRows.stringValuesSet("machine");
        Assert.assertEquals(Set.of(PresentUtils.computerName), setMachines);
    }

    /*
    See also:
        uint32 Win32_Process.GetAvailableVirtualSize(
          [out] uint64 AvailableVirtualSize
        );
     */

}
