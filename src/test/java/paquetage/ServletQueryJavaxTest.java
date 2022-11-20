package paquetage;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ServletQueryJavaxTest {
    @Test
    public void Constructor() {
        ServletQueryJavax servlet = new ServletQueryJavax();
        Assert.assertNotEquals(null, servlet);
    }
}
