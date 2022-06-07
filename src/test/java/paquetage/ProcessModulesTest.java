package paquetage;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

public class ProcessModulesTest {
    @Test
    public void GetAll_1() throws Exception {
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);
        String path = System.getProperty("java.home");
        String boot = System.getProperty("sun.boot.library.path");
        java.util.Optional<String> currExeOpt = ProcessHandle.current().info().command();
        String currExe = currExeOpt.get();

        Map<String, ArrayList<String>> result = ProcessModules.GetAll();

        Assert.assertTrue(result.containsKey(pidString));
        System.out.println("currExe=" + currExe);
        System.out.println("result.get(pidString)=" + result.get(pidString));
        Assert.assertEquals(currExe, result.get(pidString).get(0));
        /*
        Assert.assertEquals(1, pathMap.size());
        Assert.assertEquals("C:\\WINDOWS\\SYSTEM32\\HologramWorld.dll", pathMap.get("Name"));

         */
    }

}
