package regression;

import com.microchip.mplab.nbide.embedded.arduino.importer.Platform;
import com.microchip.mplab.nbide.embedded.arduino.importer.PlatformFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jruby.util.ByteList;
import org.jvyamlb.YAML;
import org.testng.Assert;

public class RegressionTestConfig {

    public static RegressionTestConfig loadFromStream(InputStream input) throws IOException, URISyntaxException {
        Map configuration = (Map) YAML.load(input);

        RegressionTestConfig testConfig = new RegressionTestConfig();
        testConfig.arduinoInstallPath = readPathFromConfiguration( configuration, "arduinoInstallDir" );
        testConfig.xc32ToolchainPath = readPathFromConfiguration( configuration, "xc32ToolchainDir" );
        testConfig.avrToolchainPath = readPathFromConfiguration( configuration, "avrToolchainDir" );
        testConfig.samdToolchainPath = readPathFromConfiguration( configuration, "samdToolchainDir" );
        testConfig.userSettingsPath = readPathFromConfiguration( configuration, "userSettingsDir" );
        testConfig.testAreaPath = readPathFromConfiguration( configuration, "testAreaDir" );
        testConfig.platforms = parsePlatforms(testConfig.userSettingsPath, (List) configuration.get(ByteList.create("platforms")));
        testConfig.commonProjectPaths = parseCommonProjectPaths(testConfig.arduinoInstallPath, (List) configuration.get(ByteList.create("commonProjects")));

        return testConfig;
    }

    private static Path readPathFromConfiguration( Map config, String pathKey ) throws URISyntaxException {
        Object value = config.get( ByteList.create(pathKey) );
        if ( value != null ) {
            return parsePath( value.toString() );
        } else {
            return null;
        }
    }
    
    private static Path parsePath(String pathString) throws URISyntaxException {
        StringBuilder b = new StringBuilder();
        if (pathString.startsWith("~")) {
            b.append(System.getProperty("user.home"));
            if (!(pathString.charAt(1) == '/' || pathString.charAt(1) == '\\')) {
                b.append(File.separatorChar);

            }
            b.append(pathString.subSequence(1, pathString.length()));
        } else {
            b.append(pathString);
        }
        Path p = Paths.get(b.toString());
        if (p.isAbsolute()) {
            return p;
        } else {  // A relative path will be relative to the project root
            Path classesPath = Paths.get(regression.RegressionTestConfig.class.getResource("/").toURI());
            Path rootPath = classesPath.resolve("../../../../").normalize();
            return rootPath.resolve(p);
        }
    }

    private static List<PlatformTestConfig> parsePlatforms(Path userSettingsPath, List platformsConfig) {
        List<PlatformTestConfig> ret = new ArrayList<>();

        platformsConfig.forEach(p -> {
            Map.Entry entry = (Map.Entry) ((Map) p).entrySet().iterator().next();

            PlatformTestConfig platformTestConfig = new PlatformTestConfig();

            String[] vendorArchitecturePair = entry.getKey().toString().split("/");

            try {
                Platform platform = new PlatformFactory().createPlatform(userSettingsPath, vendorArchitecturePair[0], vendorArchitecturePair[1]);
                platformTestConfig.platform = platform;
                platformTestConfig.boardIDs.addAll( platform.getBoardIDs() );
            } catch (IOException ex) {
                ex.printStackTrace();
                Assert.fail("Failed to find platform for " + entry.getKey());
            }

            final Path rootHardwarePath = findRootHardwarePath(userSettingsPath, platformTestConfig.platform.getVendor(), platformTestConfig.platform.getArchitecture());
            if (rootHardwarePath == null) {
                Assert.fail("Failed to find root hardware path for " + platformTestConfig.platform.getDisplayName());
            }

            Map parametersMap = (Map) entry.getValue();
            if (parametersMap != null) {
                List excludedBoardIds = (List) parametersMap.get(ByteList.create("excludeBoards"));
                if (excludedBoardIds != null) {
                    excludedBoardIds.forEach(id -> {
                        platformTestConfig.boardIDs.remove(id.toString());
                    });
                }

                List includedProjectsRootDir = (List) parametersMap.get(ByteList.create("includeProjectsFrom"));
                if (includedProjectsRootDir != null) {
                    includedProjectsRootDir.forEach(dir -> {
                        Path projectRootPath = rootHardwarePath.resolve(dir.toString());
                        if (Files.exists(projectRootPath)) {
                            List<Path> allProjectsInDirectory = findAllProjectsUnder(projectRootPath);
                            platformTestConfig.additionalProjectPaths.addAll(allProjectsInDirectory);
                        } else {
                            throw new IllegalArgumentException("Can't find project \"" + p + "\" under \"" + rootHardwarePath + "\"");
                        }
                    });
                }
            }
            ret.add(platformTestConfig);
        });

        return ret;
    }

    private static Path findRootHardwarePath(Path userSettingsPath, String vendor, String architecture) {
        Optional<Path> opt1 = findChildDirectoryPath(userSettingsPath.resolve("packages"), vendor);
        if (opt1.isPresent()) {
            Optional<Path> opt2 = findChildDirectoryPath(opt1.get().resolve("hardware"), architecture);
            if (opt2.isPresent()) {
                try {
                    Optional<Path> opt3 = Files.list(opt2.get()).findFirst();
                    return opt3.get();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return null;
    }

    private static Optional<Path> findChildDirectoryPath(Path parent, String childDirName) {
        try {
            String childDirNameLC = childDirName.toLowerCase();
            return Files.list(parent).filter(p -> p.getFileName().toString().toLowerCase().equals(childDirNameLC)).findAny();
        } catch (IOException ex) {
            Assert.fail("Failed to find directory \"" + childDirName + "\" under " + parent);
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    private static List<Path> parseCommonProjectPaths(Path arduinoInstallPath, List commonProjectsConfig) {
        List<Path> ret = new ArrayList<>();
        if ( commonProjectsConfig != null ) {
            Path examplesPath = arduinoInstallPath.resolve("examples");        
            commonProjectsConfig.forEach(p -> {
                Path projectPath = examplesPath.resolve(p.toString());
                if (Files.exists(projectPath)) {
                    ret.add(projectPath);
                } else {
                    throw new IllegalArgumentException("Can't find project \"" + p + "\" under \"" + arduinoInstallPath + "\"");
                }
            });
        }
        return ret;
    }

    private static List<Path> findAllProjectsUnder(Path projectRootPath) {
        final List<Path> ret = new ArrayList<>();
        try {
            Files.walkFileTree(projectRootPath, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().endsWith(".ino")) {
                        ret.add(file.getParent());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            Assert.fail("Failed to find projects under \"" + projectRootPath + "\"");
            ex.printStackTrace();
        }

        return ret;
    }

    private Path arduinoInstallPath;
    private Path xc32ToolchainPath;
    private Path avrToolchainPath;
    private Path samdToolchainPath;
    private Path userSettingsPath;
    private Path testAreaPath;
    private List<PlatformTestConfig> platforms;
    private List<Path> commonProjectPaths;

    @Override
    public String toString() {
        return "Test Configuration\narduinoInstallPath = " + arduinoInstallPath
                + "\nxc32ToolchainPath = " + xc32ToolchainPath
                + "\navrToolchainPath = " + avrToolchainPath
                + "\nsamdToolchainPath = " + samdToolchainPath
                + "\nuserSettingsPath = " + userSettingsPath
                + "\ntestAreaPath = " + testAreaPath
                + "\nplatforms = " + platforms
                + "\ncommonProjectPaths = " + commonProjectPaths;
    }

    private RegressionTestConfig() {
        // Empty constructor
    }

    public Path getArduinoInstallPath() {
        return arduinoInstallPath;
    }

    public Path getUserSettingsPath() {
        return userSettingsPath;
    }

    public Path getXC32ToolchainPath() {
        return xc32ToolchainPath;
    }
    
    public Path getAVRToolchainPath() {
        return avrToolchainPath;
    }
    
    public Path getSAMDToolchainPath() {
        return samdToolchainPath;
    }

    public List<Path> getCommonProjectPaths() {
        return commonProjectPaths;
    }

    public List<PlatformTestConfig> getPlatformTestConfigs() {
        return platforms;
    }

    public Path getTestAreaPath() {
        return testAreaPath;
    }

    public static class PlatformTestConfig {

        private Platform platform;
        private List<String> boardIDs = new ArrayList<>();
        private List<Path> additionalProjectPaths = new ArrayList<>();

        public Platform getPlatform() {
            return platform;
        }

        public List<String> getBoardIDs() {
            return boardIDs;
        }

        public List<Path> getAdditionalProjectPaths() {
            return additionalProjectPaths;
        }
        
        @Override
        public String toString() {
            return "\n" + platform.getDisplayName() + "\n\tboardIDs = " + boardIDs + "\n\tadditionalProjectPaths = " + additionalProjectPaths;
        }

    }

}
