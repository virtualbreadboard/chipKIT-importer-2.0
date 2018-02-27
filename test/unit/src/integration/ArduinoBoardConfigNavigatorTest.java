package integration;


import com.microchip.mplab.nbide.embedded.arduino.importer.ArduinoConfig;
import com.microchip.mplab.nbide.embedded.arduino.importer.BoardConfigNavigator;
import com.microchip.mplab.nbide.embedded.arduino.importer.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ArduinoBoardConfigNavigatorTest {
        
    private static Path testVariantsDirPath;
    private static Platform platform;
            
    @BeforeClass
    public static void setupTestBoardsFile() throws Exception {
        platform = BoardConfigNavigator.findPlatform( "arduino", "avr", ArduinoConfig.getInstance().getSettingsPath() );
        testVariantsDirPath = platform.getRootPath().resolve("variants");
    }
    
    private BoardConfigNavigator configNavigator;
    
    @Before
    public void setup() {
        configNavigator = new BoardConfigNavigator( platform );
    }
    
    @Test
    public void should_find_26_Arduino_boards() throws IOException  {
        Map <String,String> boardIdsAndNames = configNavigator.parseBoardNamesToIDsLookup();
        Assert.assertNotNull( "Board name to ID map cannot be null", boardIdsAndNames );
        Assert.assertEquals("Board name to ID map must have 26 entries", 26, boardIdsAndNames.size() );
    }

    @Test
    public void leonardo_should_have_atmega32u4_mcu() throws IOException  {
        Optional<String> mcu = configNavigator.parseMCU("leonardo");
        Assert.assertEquals("MCU for Leonardo should be atmega32u4", "atmega32u4", mcu.get() );
    }

    @Test
    public void should_find_proper_path_for_variant_without_a_boards_txt_file() throws IOException  {
        Path path = configNavigator.getVariantPath("leonardoeth");
        Assert.assertEquals("Failed to find the proper variant directory", testVariantsDirPath.resolve("leonardo"), path );
    }

    @Test
    public void should_find_proper_path_for_variant_with_a_boards_txt_file() throws IOException  {
        Path path = configNavigator.getVariantPath("leonardo");
        Assert.assertEquals("Failed to find the proper variant directory", testVariantsDirPath.resolve("leonardo"), path );
    }
    
    @Test
    public void should_find_programmer_path() throws IOException  {
        String progFilename = configNavigator.getProgrammerFilename();        
        Path path = configNavigator.getProgrammerPath();
        Assert.assertNotNull( "Programmer filename cannot be null!", progFilename );
        Assert.assertTrue( "Failed to find " + progFilename, path != null && Files.exists(path) );
        Assert.assertTrue( "Programmer file must be executable", Files.isExecutable( path ) );
        Assert.assertEquals( "Programmer path does not end with programmer filename provided by the navigator", path.getFileName().toString(), progFilename );
    }
    
    @Test
    public void should_find_one_arduino_AVR_platform() throws IOException  {
        List<Platform> platforms = BoardConfigNavigator.findPlatforms( ArduinoConfig.getInstance().getSettingsPath() );
        long count = platforms.stream().filter( p -> "arduino".equals(p.getVendor()) && "avr".equals(p.getArchitecture()) ).count();
        Assert.assertEquals( "There should be exactly one Arduino/AVR platform", 1, count );
    }
    
    @Test
    public void should_find_one_arduino_SAMD_platform() throws IOException  {
        List<Platform> platforms = BoardConfigNavigator.findPlatforms( ArduinoConfig.getInstance().getSettingsPath() );
        long count = platforms.stream().filter( p -> "arduino".equals(p.getVendor()) && "samd".equals(p.getArchitecture()) ).count();
        Assert.assertEquals( "There should be exactly one Arduino/SAMD platform", 1, count );
    }
    
    @Test
    public void should_list_all_platforms() throws IOException  {
        List<Platform> platforms = BoardConfigNavigator.findPlatforms( ArduinoConfig.getInstance().getSettingsPath() );
        Assert.assertTrue( "No platform found", platforms.size() > 0 );
        System.out.println("--- All available platforms ---");
        platforms.forEach( p -> System.out.println(p.getDisplayName()) );
        System.out.println("");
    }
    
    
}
