package regression;

import com.microchip.mplab.nbide.embedded.arduino.importer.Board;
import utils.TestUtilities;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.internal.runners.statements.Fail;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class RegressionTestRunner extends BlockJUnit4ClassRunner {

    private static final String CONFIG_FILE_NAME = "config.yml";
    
    private RegressionTestConfig config;

    public RegressionTestRunner(Class<?> c) throws InitializationError {
        super(c);        
    }

    @Override
    protected void validateTestMethods(List<Throwable> errors) {        
        validateConfig(errors);
        super.validateTestMethods(errors);
    }
    
    private void validateConfig( List<Throwable> errors ) {
        if ( config == null || config.getPlatformTestConfigs() == null ) {
            try {
                loadConfigFile();
                TestUtilities.clearDirectory(config.getTestAreaPath());
            } catch (Exception ex) {
                errors.add(ex);
            }
        }
        
        if ( !Files.exists( config.getAVRToolchainPath() ) ) {
            errors.add( new RuntimeException("AVR toolchain not found! Please check the paths in the config.yml file") );
        }
        
        if ( !Files.exists( config.getXC32ToolchainPath()) ) {
            errors.add( new RuntimeException("XC32 toolchain not found! Please check the paths in the config.yml file") );
        }
        
        if ( !Files.exists( config.getSAMDToolchainPath()) ) {
            errors.add( new RuntimeException("SAMD toolchain not found! Please check the paths in the config.yml file") );
        }
        
        if ( !Files.exists( config.getArduinoInstallPath() ) ) {
            errors.add( new RuntimeException("Arduino install directory not found! Please check the paths in the config.yml file") );
        }
        
        if ( !Files.exists( config.getUserSettingsPath()) ) {
            errors.add( new RuntimeException("User settings directory not found! Please check the paths in the config.yml file") );
        }
    }

    @Override
    protected void validateZeroArgConstructor(List<Throwable> errors) {
        // Ignore this - we don't have a zero argument constructor
    }

    protected Object createTest(FrameworkMethod m) throws Exception {
        ImporterTestMethod itm = (ImporterTestMethod) m;
        Files.createDirectories( itm.targetProjectPath );
        
//        if ( Files.exists( itm.targetProjectPath ) ) {
//            Files.createDirectories( itm.targetProjectPath.resolve( RegressionTest.RELATIVE_MAIN_BUILD_DIRECTORY_PATH ) );
//            Files.createDirectories( itm.targetProjectPath.resolve( RegressionTest.RELATIVE_LIBRARIES_BUILD_DIRECTORY_PATH ) );
//        }
        
        return getTestClass().getOnlyConstructor().newInstance( 
            config,
            itm.vendor,
            itm.architecture,
            itm.boardId, 
            itm.boardCpu, 
            itm.sourceProjectPath, 
            itm.targetProjectPath
        );
    }

    @Override
    protected Statement methodBlock(final FrameworkMethod method) {
        Object test;
        try {
            test = new ReflectiveCallable() {
                @Override
                protected Object runReflectiveCall() throws Throwable {
                    return createTest(method);
                }
            }.run();
        } catch (Throwable e) {            
            return new Fail(e);
        }
        Statement statement = methodInvoker(method, test);
        statement = withBefores(method, test, statement);
        statement = withAfters(method, test, statement);
        return statement;
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        try {            
            Path examplesPath = config.getArduinoInstallPath().resolve("examples");
            Path testAreaPath = config.getTestAreaPath();
            Method method = RegressionTest.class.getMethod("test");
            List<FrameworkMethod> ret = new ArrayList<>();
            List<Path> commonProjectPaths = config.getCommonProjectPaths();
            
            for ( RegressionTestConfig.PlatformTestConfig ptc : config.getPlatformTestConfigs() ) {
                List<Path> platformProjectPaths = ptc.getAdditionalProjectPaths();
                Path rootHardwarePath = ptc.getPlatform().getRootPath();
                
                for ( String boardId : ptc.getBoardIDs() ) {
                    Board board = ptc.getPlatform().getBoard(boardId);
                    List <String> cpus = new ArrayList<> (board.getCPUs());
                    if ( cpus.isEmpty() ) {
                        // Add an empty string to CPUs list to make sure that there is always at least one test method created
                        cpus.add("");
                    }
                    
                    for ( String cpu : cpus ) {
                        Path boardTestAreaPath = testAreaPath.resolve( ptc.getPlatform().getVendor() ).resolve( ptc.getPlatform().getArchitecture() ).resolve( boardId );
                        if ( !cpu.isEmpty() ) {
                            boardTestAreaPath = boardTestAreaPath.resolve( cpu );
                        }

                        // Common sample projects:
                        for (Path sourceProjectPath : commonProjectPaths) {
                            Path relativeProjectPath = examplesPath.relativize( sourceProjectPath );
                            Path targetProjectPath = boardTestAreaPath.resolve(relativeProjectPath);
                            addTestMethodsToList(ret, method, ptc, boardId, cpu, sourceProjectPath, targetProjectPath);
                        }

                        // Platform specific sample projects:
                        for (Path sourceProjectPath : platformProjectPaths) {                        
                            Path relativeProjectPath = rootHardwarePath.relativize( sourceProjectPath );
                            Path targetProjectPath = boardTestAreaPath.resolve(relativeProjectPath);                        
                            addTestMethodsToList(ret, method, ptc, boardId, cpu, sourceProjectPath, targetProjectPath);
                        }
                    }
                }
            }
            return ret;
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    
    private void addTestMethodsToList( List<FrameworkMethod> ret, Method method, RegressionTestConfig.PlatformTestConfig ptc, String boardId, String cpu, Path sourceProjectPath, Path targetProjectPath ) {
        ret.add( new ImporterTestMethod(
            method, ptc.getPlatform().getVendor(), ptc.getPlatform().getArchitecture(), boardId, cpu, sourceProjectPath, targetProjectPath
        ) );
    }

    private void loadConfigFile() throws InitializationError, URISyntaxException {
        try (InputStream input = getClass().getResourceAsStream( CONFIG_FILE_NAME )) {
            config = RegressionTestConfig.loadFromStream(input);
        } catch (IOException ex) {
            throw new InitializationError(ex);
        }
    }

    private static class ImporterTestMethod extends FrameworkMethod {

        private final String vendor;
        private final String architecture;
        private final String boardId;
        private final String boardCpu;        
        private final Path sourceProjectPath;
        private final Path targetProjectPath;

        public ImporterTestMethod(Method method, String vendor, String architecture, String boardId, String boardCpu, Path sourceProjectPath, Path targetProjectPath) {
            super(method);
            this.vendor = vendor;
            this.architecture = architecture;
            this.boardId = boardId;
            this.boardCpu = boardCpu;
            this.sourceProjectPath = sourceProjectPath;
            this.targetProjectPath = targetProjectPath;
        }

        @Override
        public String getName() {
            return vendor + ":" + architecture + ":" + boardId + ( (boardCpu != null && !boardCpu.isEmpty()) ? ":cpu=" + boardCpu : "") + " " + sourceProjectPath;
        }
    }

}
