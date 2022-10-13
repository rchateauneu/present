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
    public void testSelectWin32_Process_PSComputerName_Where() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?handle
                    where {
                        ?process cimv2:Win32_Process.PSComputerName "%s" .
                        ?process cimv2:Win32_Process.Handle ?handle .
                    }
                """, PresentUtils.computerName);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setHandles = listRows.StringValuesSet("handle");
        Assert.assertTrue(setHandles.contains(currentPidStr));
    }

    /** This gets machines of all processes. The intention of this test is to check how the column
     * "PSComputerName" is processed. */
    @Test
    public void testSelectWin32_Process_PSComputerName_Select() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?machine
                    where {
                        ?process cimv2:Win32_Process.PSComputerName ?machine .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setMachines = listRows.StringValuesSet("machine");
        Assert.assertEquals(Set.of(PresentUtils.computerName), setMachines);
    }

    /*
    Win32_Process.GetOwner() method to get the owner and domain.
            uint32 GetOwner(
              [out] string User,   # Returns the user name of the owner of this process.
              [out] string Domain  # Returns the domain name under which this process is running.
            );                     # Returns zero (0) to indicate success.
        How to call methods ? We cannot add parentheses in a Sparql token.
        Maybe consider that if a method name is used as a property, then it is a getter,
        and can be used as such.
        However, it is very dangerous because calling a method could have a destructive effect.
        Also, there are apparently very few methods which in fact are simply getters.

        Is it possible to introspect arguments of a WMI class method ?

        Possible add a kind of virtual attribute in a custom provider only.
        But then, this predicate should be added to the ontology.
    */
    @Ignore("Not designed nor implemented yet")
    @Test
    public void testSelectWin32_Process_GetOwner() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?owner
                    where {
                        ?process cimv2:Win32_Process.GetOwner ?owner .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setMachines = listRows.StringValuesSet("machine");
        Assert.assertEquals(Set.of(PresentUtils.computerName), setMachines);
    }

    /*
    See also:
        uint32 Win32_Process.GetAvailableVirtualSize(
          [out] uint64 AvailableVirtualSize
        );
     */

}
