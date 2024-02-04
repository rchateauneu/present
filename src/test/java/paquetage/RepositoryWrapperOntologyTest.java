package paquetage;

import org.junit.*;

import java.util.Set;
import java.util.stream.Collectors;


public class RepositoryWrapperOntologyTest {


    static long currentPid = ProcessHandle.current().pid();
    static String currentPidStr = String.valueOf(currentPid);

    private RepositoryWrapper repositoryWrapper = null;

    @Before
    public void setUp() throws Exception {
        repositoryWrapper = new RepositoryWrapper();
    }

    //@Override
    @After
    public void tearDown() throws Exception {
        repositoryWrapper = null;
    }

    /** This returns all classes with name "__Win32Provider" form all namespaces.
     *
     __PATH                        : \\LAPTOP-R89KG6V1\ROOT\cimv2:__Win32Provider.Name="SystemConfigurationChangeEvents"
     ClientLoadableCLSID           :
     CLSID                         : {D31B6A3F-9350-40de-A3FC-A7EDEB9B7C63}
     Concurrency                   :
     DefaultMachineName            :
     Enabled                       :
     HostingModel                  : LocalSystemHost
     ImpersonationLevel            : 0
     InitializationReentrancy      : 0
     InitializationTimeoutInterval :
     InitializeAsAdminFirst        :
     Name                          : SystemConfigurationChangeEvents
     OperationTimeoutInterval      :
     PerLocaleInitialization       : False
     PerUserInitialization         : False
     Pure                          : True
     SecurityDescriptor            :
     SupportsExplicitShutdown      :
     SupportsExtendedStatus        :
     SupportsQuotas                :
     SupportsSendStatus            :
     SupportsShutdown              :
     SupportsThrottling            :
     UnloadTimeout                 :
     Version                       :
     PSComputerName                : LAPTOP-R89KG6V1
    */
    @Test
    public void testSelect_ProvidersClasses() throws Exception {
        String sparqlQuery = """
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    prefix wmi:  <http://www.primhillcomputers.com/ontology/>
                    select ?namespace
                    where {
                        ?class rdfs:label "\\"__Win32Provider\\"@en" .
                        ?class wmi:NamespaceDefinition ?namespace .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        Set<String> setNamespaces = listRows.stream().map(tuple -> tuple.getAsLiteral("namespace")).collect(Collectors.toSet());
        System.out.println("setNamespaces=" + setNamespaces);
        Assert.assertTrue(setNamespaces.contains("ROOT\\RSOP"));
        Assert.assertTrue(setNamespaces.contains("ROOT\\StandardCimv2"));
    }

    /*
        Ajouter le namespace dans les classes ?
        prefix meta:  <http://www.primhillcomputers.com/ontology/MetaData#>
            ?class meta:Namespace ?namespace
            ?class rdfs:label "__Win32Provider" .


    Si on ajoutait quelque chose pour forcer le chargement du namespace ?
    Les prefixes ne sont que du sucre syntaxique.
    ?class namespace "machin"

    tester ce qui se passe avec Values: Est-ce interprete avant ??

     * Apparemment, la query ne rend pas les prefix.
     * On peut en revanche les trouver quand on "meet" les statements et les classes.
     * Et de toute facon, ca ne si
     * Mais ca ne suffirait pas si une classe est une variable.
     * Notre parsing peut renvoyer les prefixes trouves ou bien "*" si on rencontre metadata.
     * ?class rdf:type rdfs:Class
     * ?class metadata:namespace ?namespace

     * A ce moment, on peut charger les ontologies.
     *
     * Si une classe est une variable, que faire ?
     * ?class rdf:type rdfs:Class
     * ?class metadata:namespace ?namespace
     * ?object rdf:type ?class
     (1) Instancier en premier lieu toutes les classes.
     (2) Il faut demultiplier tous les objects patterns.

     Ca serait vraiment plus simple de charger toutes les ontologies mais ca risque d'etre lent.
     En principe, il suffirait de filtrer l'IRI.
     *

     Quand la classe est variable, la query est equivalente a une UNION avec un element par classe.
     Il separer les statements entre ceux qui peuvent fournir la classe est les autres qui seront executes
     normalement.
     */

    /** This returns all classes with name "__Win32Provider" form all namespaces.
     *
     __PATH                        : \\LAPTOP-R89KG6V1\ROOT\cimv2:__Win32Provider.Name="SystemConfigurationChangeEvents"
     ClientLoadableCLSID           :
     CLSID                         : {D31B6A3F-9350-40de-A3FC-A7EDEB9B7C63}
     Concurrency                   :
     DefaultMachineName            :
     Enabled                       :
     HostingModel                  : LocalSystemHost
     ImpersonationLevel            : 0
     InitializationReentrancy      : 0
     InitializationTimeoutInterval :
     InitializeAsAdminFirst        :
     Name                          : SystemConfigurationChangeEvents
     OperationTimeoutInterval      :
     PerLocaleInitialization       : False
     PerUserInitialization         : False
     Pure                          : True
     SecurityDescriptor            :
     SupportsExplicitShutdown      :
     SupportsExtendedStatus        :
     SupportsQuotas                :
     SupportsSendStatus            :
     SupportsShutdown              :
     SupportsThrottling            :
     UnloadTimeout                 :
     Version                       :
     PSComputerName                : LAPTOP-R89KG6V1
     */
    @Ignore("Disabled until classes can be variables")
    @Test
    public void testSelect_ProvidersInstances() throws Exception {
        String sparqlQuery = """
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?class ?predicate ?value
                    where {
                        ?class rdfs:label "__Win32Provider" .
                        ?class ?predicate ?value .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        Assert.assertTrue(false);
    }
}

