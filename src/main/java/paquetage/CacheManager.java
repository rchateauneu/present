package paquetage;

import org.apache.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CacheManager {
    final static private Logger logger = Logger.getLogger(CacheManager.class);
    public static Path ontologiesPathCache;

    // To cleanup the ontology, this entire directory must be deleted, and not only its content.
    static Path PathNamespacePrefix(String namespace) {
        // The namespace might contain backslashes, but this is OK on Windows.
        return Paths.get(ontologiesPathCache + "\\" + namespace);
    }

    static File DirSailDump(String namespace) throws Exception {
        // The namespace might contain backslashes, but this is OK on Windows.
        Path pathNamespacePrefix = PathNamespacePrefix(namespace);

        checkCacheDirectoryExists();
        //Files.createDirectories(ontologiesPathCache);
        File dirSaildump = new File(pathNamespacePrefix + ".SailDir");
        logger.debug("dirSaildump=" + dirSaildump);
        return dirSaildump;
    }

    private static void CheckDirectoryExists(Path dirPath) {
        boolean dirExists = Files.exists(dirPath);
        if(! dirExists) {
            File file = new File(dirPath.toString());
            file.mkdirs();
        }
    }

    static File ClassesCacheFile(String namespace) {
        // The namespace might contain backslashes, but this is OK on Windows.
        Path pathNamespacePrefix = PathNamespacePrefix(namespace);
        CheckDirectoryExists(pathNamespacePrefix);

        String nameFileClassesCache = pathNamespacePrefix + ".ClassesCache.json";

        return new File(nameFileClassesCache);
    }

    public static void checkCacheDirectoryExists() {
        CheckDirectoryExists(ontologiesPathCache);
    }

    static {
        String tempDir = System.getProperty("java.io.tmpdir");

        // To cleanup the ontology, this entire directory must be deleted, and not only its content.
        ontologiesPathCache = Paths.get(tempDir + "\\" + "PresentOntologies");
    }
}
