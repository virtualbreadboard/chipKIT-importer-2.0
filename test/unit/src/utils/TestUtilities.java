package utils;

import com.microchip.mplab.nbide.embedded.arduino.utils.DeletingFileVisitor;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Adapted from http://www.codejava.net/java-se/file-io/programmatically-extract-a-zip-file-using-java
public final class TestUtilities {

    private TestUtilities() {
    }

    private static final int BUFFER_SIZE = 4096;

    public static void clearDirectory(Path directoryPath) throws IOException {
        if (Files.exists(directoryPath)) {
            Files.walkFileTree(directoryPath, new DeletingFileVisitor());
        }
        if (!Files.exists(directoryPath)) {
            Files.createDirectory(directoryPath);
        }
    }

    public static void unzip(Path zipFilePath, Path destDirectoryPath) throws IOException {
        unzip(Files.newInputStream(zipFilePath), destDirectoryPath);
    }

    public static void unzip(InputStream inputStream, Path destDirectoryPath) throws IOException {
        if (!Files.exists(destDirectoryPath)) {
            Files.createDirectory(destDirectoryPath);
        }

        ZipInputStream zipIn = new ZipInputStream(inputStream);
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            Path filePath = destDirectoryPath.resolve(entry.getName());
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                Files.createDirectory(filePath);
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }

    public static boolean pathContains(Path path, String elemenet) {
        Iterator<Path> it = path.iterator();
        while (it.hasNext()) {
            if (it.next().getFileName().toString().equals(elemenet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts a zip entry (file entry)
     *
     * @param zipIn
     * @param filePath
     * @throws IOException
     */
    private static void extractFile(ZipInputStream zipIn, Path filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

}
