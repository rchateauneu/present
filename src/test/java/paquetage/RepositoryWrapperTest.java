package paquetage;

import junit.framework.TestCase;
import org.junit.Test;

public class RepositoryWrapperTest extends TestCase {
    @Test
    public static void testFromMemory() throws Exception {
        new RepositoryWrapper().CreateSailRepositoryFromMemory();
    }

    @Test
    public static void testFromDir() throws Exception {
        new RepositoryWrapper().CreateSailRepositoryFromFile(
                ".");
    }
}