package paquetage;

import junit.framework.TestCase;
import org.junit.Test;

public class CreateRepositoryTest extends TestCase {
    @Test
    public static void testFromMemory() throws Exception {
        CreateRepository.CreateSailRepositoryFromMemory();
    }

    @Test
    public static void testFromDir() throws Exception {
        CreateRepository.CreateSailRepositoryFromFile(
                ".");
    }
}