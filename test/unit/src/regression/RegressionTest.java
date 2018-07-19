package regression;


import com.microchip.mplab.nbide.embedded.arduino.importer.ArduinoBuilderRunner;
import com.microchip.mplab.nbide.embedded.arduino.importer.ArduinoConfig;
import com.microchip.mplab.nbide.embedded.arduino.importer.BootloaderPathProvider;
import com.microchip.mplab.nbide.embedded.arduino.importer.ProjectImporter;
import com.microchip.mplab.nbide.embedded.arduino.importer.GCCToolFinder;
import static com.microchip.mplab.nbide.embedded.arduino.importer.ProjectImporter.*;
import com.microchip.mplab.nbide.embedded.arduino.importer.Board;
import com.microchip.mplab.nbide.embedded.arduino.importer.BoardId;
import com.microchip.mplab.nbide.embedded.arduino.importer.Platform;
import com.microchip.mplab.nbide.embedded.arduino.importer.PlatformFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


// TODO: Consider running instances of this test in parallel but be careful about logging
@RunWith(RegressionTestRunner.class)
public class RegressionTest {

    
    public static final Path RELATIVE_MAIN_BUILD_DIRECTORY_PATH = Paths.get( "build", "default", "production", SOURCE_FILES_DIRECTORY_NAME );
    public static final Path RELATIVE_LIBRARIES_BUILD_DIRECTORY_PATH = Paths.get( "build", "default", "production", LIBRARIES_DIRECTORY_NAME );
    
    private static final Logger LOGGER = Logger.getLogger( RegressionTest.class.getName() );
    
    // TODO: The static log handler makes it impossible to run tests in parallel - consider refactoring
    private static FileHandler testRunFileLogHandler;

    private final Path arduinoInstallPath;
    private final Path toolchainPath;
    private final String vendor;
    private final String architecture;
    private final BoardId boardId;
    private final Path sourceProjectPath;
    private final Path targetProjectPath;
    
    
    public RegressionTest( RegressionTestConfig config, String vendor, String architecture, BoardId boardId, Path sourceProjectPath, Path targetProjectPath ) {
        this.arduinoInstallPath = config.getArduinoInstallPath();        
        this.vendor = vendor;
        this.architecture = architecture;                
        this.boardId = boardId;
        this.sourceProjectPath = sourceProjectPath;
        this.targetProjectPath = targetProjectPath;
        switch (architecture.toLowerCase()) {
            case "pic32":
                this.toolchainPath = config.getXC32ToolchainPath();
                break;
            case "samd":
                this.toolchainPath = config.getSAMDToolchainPath();
                break;
            default:
                this.toolchainPath = config.getAVRToolchainPath();
                break;
        }
    }
    
    @Before
    public void setupLogger() throws IOException {
        Logger rootLogger = Logger.getLogger("");
        if ( testRunFileLogHandler != null ) {
            rootLogger.removeHandler(testRunFileLogHandler);
        }
        testRunFileLogHandler = new FileHandler( targetProjectPath.resolve("import.log").toString() );
        testRunFileLogHandler.setFilter(null);
        testRunFileLogHandler.setFormatter( new Formatter() {
            @Override
            public String format(LogRecord record) {
                Object[] parameters = record.getParameters();
                if ( parameters != null && parameters.length > 0 ) {
                    return MessageFormat.format(record.getMessage(), parameters)+"\n";
                } else {
                    return record.getMessage()+"\n";
                }
            }            
        } );
        rootLogger.addHandler( testRunFileLogHandler );
    }
    
    @Test
    public void test() throws Exception {
        Assert.assertNotNull( arduinoInstallPath );
        Assert.assertNotNull( toolchainPath );
        Assert.assertNotNull( vendor );
        Assert.assertNotNull( architecture );
        Assert.assertNotNull( boardId );
        Assert.assertNotNull( sourceProjectPath );
        Assert.assertNotNull( targetProjectPath );
        
        // Given        
        final boolean copyFiles = false;
        final GCCToolFinder toolFinder = new GCCToolFinder( toolchainPath );
        
        final ArduinoConfig arduinoConfig = ArduinoConfig.getInstance();
        final Platform platform = new PlatformFactory().createPlatform( ArduinoConfig.getInstance().getSettingsPath(), vendor, architecture );
        final Board board = platform.getBoard(boardId);
        
        final ArduinoBuilderRunner arduinoBuilderRunner = new ArduinoBuilderRunner(toolFinder, arduinoConfig, arduinoInstallPath, LOGGER::info );
                
        Path classesPath = Paths.get( getClass().getResource("/").toURI() );
        final Path bootloadersPath =  classesPath.resolve("../../../../release/bootloaders/").normalize();
        final BootloaderPathProvider bootloaderPathProvider = new BootloaderPathProvider( (filename) -> {
            return bootloadersPath.resolve(filename);
        });
        final Path customLinkerScriptsPath =  classesPath.resolve("../../../../release/linker_scripts/").normalize();
        
        // When
        ProjectImporter importer = new ProjectImporter();
        importer.setCopyingFiles( copyFiles );
        importer.setBoard( board );
        importer.setSourceProjectDirectoryPath( sourceProjectPath );
        importer.setTargetProjectDirectoryPath( targetProjectPath );
        importer.setArduinoBuilderRunner( arduinoBuilderRunner );
        importer.setBootloaderPathProvider(bootloaderPathProvider);
        importer.setCustomLdScriptsPath( customLinkerScriptsPath );
        importer.execute();
                                
        // Then
        ProjectBuilder b = new ProjectBuilder();
        b.build( 
            targetProjectPath,
            targetProjectPath.resolve( RELATIVE_MAIN_BUILD_DIRECTORY_PATH ), 
            importer,
            LOGGER::info,
            LOGGER::severe
        );
        
        LOGGER.log(Level.INFO, "Project Path: {0}", targetProjectPath);
        LOGGER.info("\nSource Files:");
        importer.getSourceFilePaths().forEach( p -> LOGGER.info(p.toString()) );
        LOGGER.info("\nCore Files:");
        importer.getCoreFilePaths().forEach( p -> LOGGER.info(p.toString()) );
        LOGGER.info("\nMain Library Directories:");
        importer.getMainLibraryDirPaths().forEach( p -> LOGGER.info(p.toString()) );
        LOGGER.info("\nAuxiliary Library Directories:");
        importer.getAuxLibraryDirPaths().forEach( p -> LOGGER.info(p.toString()) );
        LOGGER.info("\nPreprocessing Command:");
        LOGGER.info( importer.getPreprocessingCommand() );
        LOGGER.info("\n\nAll done\n\n");
    }
    
    @After
    public void closeLogger() {
        if ( testRunFileLogHandler != null ) {
            testRunFileLogHandler.flush();                
            testRunFileLogHandler.close();
        }
    }

}
