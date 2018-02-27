package integration;


import com.microchip.mplab.nbide.embedded.arduino.importer.ArduinoConfig;
import com.microchip.mplab.nbide.embedded.arduino.importer.BoardConfigNavigator;
import com.microchip.mplab.nbide.embedded.arduino.importer.Platform;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ChipKITBoardConfigNavigatorTest {
    
    private static Path testVariantsDirPath;
    private static Platform platform;
    
    @BeforeClass
    public static void setupTestBoardsFile() throws Exception {
        platform = BoardConfigNavigator.findPlatform( "chipKIT", "pic32", ArduinoConfig.getInstance().getSettingsPath() );
        testVariantsDirPath = platform.getRootPath().resolve("variants");
    }
    
    private BoardConfigNavigator configNavigator;
    
    @Before
    public void setup() {
        configNavigator = new BoardConfigNavigator( platform );
    }
    
    @Test
    public void should_find_38_PIC32_boards() throws IOException  {
        Map <String,String> boardIdsAndNames = configNavigator.parseBoardNamesToIDsLookup();
        Assert.assertNotNull( "Board name to ID map cannot be null", boardIdsAndNames );
        Assert.assertEquals("Board name to ID map must have 38 entries", 38, boardIdsAndNames.size() );
    }
    
    @Test
    public void cerebot32mx4_should_have_32MX460F512L_mcu() throws IOException  {
        Optional<String> mcu = configNavigator.parseMCU("cerebot32mx4");            
        Assert.assertEquals("MCU should be 32MX460F512L", "32MX460F512L", mcu.get() );
    }
    
    @Test
    public void should_find_proper_path_for_variant_without_a_boards_txt_file() throws IOException  {
        Path path = configNavigator.getVariantPath("lenny");            
        Assert.assertEquals("Failed to find the proper variant directory", testVariantsDirPath.resolve("Lenny"), path );
    }
    
    @Test
    public void should_find_proper_path_for_variant_with_a_boards_txt_file() throws IOException  {
        Path path = configNavigator.getVariantPath("chipkit_uc32");
        Assert.assertEquals("Failed to find the proper variant directory", testVariantsDirPath.resolve("uC32"), path );
    }
    
    @Test
    public void should_find_one_chipKIT_PIC32_platform() throws IOException  {
        List<Platform> platforms = BoardConfigNavigator.findPlatforms( ArduinoConfig.getInstance().getSettingsPath() );
        long count = platforms.stream().filter( p -> "chipKIT".equals(p.getVendor()) && "pic32".equals(p.getArchitecture()) ).count();
        Assert.assertEquals( "There should be exactly one chipKIT/pic32 platform", 1, count );
    }    
}
