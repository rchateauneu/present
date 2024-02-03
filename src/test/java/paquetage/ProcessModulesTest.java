package paquetage;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProcessModulesTest {
    String currentPidStr = String.valueOf(ProcessHandle.current().pid());
    String currExe = ProcessHandle.current().info().command().get();

    /** This checks that the current process is in the map of processes to moodules.
     *
     * @throws Exception
     */
    @Test
    public void GetAllProcessModules_1() throws Exception {
        String path = System.getProperty("java.home");
        String boot = System.getProperty("sun.boot.library.path");

        Map<String, ArrayList<String>> result = new ProcessModules().getAllProcessesModules();

        Assert.assertTrue(result.containsKey(currentPidStr));
        System.out.println("currExe=" + currExe);
        System.out.println("result.get(currentPidStr)=" + result.get(currentPidStr));
        Assert.assertEquals(currExe, result.get(currentPidStr).get(0));
    }

    @Test
    public void GetFromModule_1() throws Exception {
        List<String> pidsList = new ProcessModules().getFromModule(currExe);
        Assert.assertTrue(pidsList.contains(currentPidStr));
    }

    @Test
    public void GetFromPid_1() throws Exception {
        List<String> modulesList = new ProcessModules().getFromPid(currentPidStr);
        Assert.assertEquals(modulesList.get(0), (currExe));
    }
}
