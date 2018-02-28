/*
 * Copyright (c) 2017 Microchip Technology Inc. and its subsidiaries (Microchip). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.microchip.mplab.nbide.embedded.arduino.importer;


import com.microchip.mplab.nbide.embedded.arduino.importer.chipkit.ChipKitBoardConfig;
import com.microchip.mplab.nbide.embedded.arduino.importer.samd.SAMDBoardConfig;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


// TODO: Remove
public class BoardConfigNavigator {
    
    private static final Logger LOGGER = Logger.getLogger(BoardConfigNavigator.class.getName());
    private static final List <String> CURRENTLY_UNSUPPORTED_BOARD_IDS = Arrays.asList("clicker2", "cui32", "CUI32stem", "usbono_pic32");
    
    public static final String BOARDS_FILENAME = "boards.txt";
    public static final String PLATFORM_FILENAME = "platform.txt";
    public static final String VARIANTS_DIRNAME = "variants";

    
    public static List<Platform> findPlatforms( Path arduinoSettingsPath ) throws IOException {
        Path settingsPath = validateArduinoSettingsPath( arduinoSettingsPath );
        
        // Find all paths containing a "platform.txt" file
        LOGGER.log(Level.INFO, "Searching for platform files in {0}", settingsPath);
        FileFinder finder = new FileFinder(PLATFORM_FILENAME);
        Files.walkFileTree(settingsPath, finder);
        List <Path> platformPaths = finder.getMatchingPaths();
        
        List <Platform> ret = platformPaths.stream()            
            .map( path -> createPlatformFromFile(path) )
            .collect( Collectors.toList() );
        
        return ret;
    }
    
    public static Platform findPlatform( String vendor, String architecture, Path arduinoSettingsPath ) throws IOException {        
        List<Platform> allPlatforms = findPlatforms(arduinoSettingsPath);
        for ( Platform p : allPlatforms ) {
            if ( vendor.equalsIgnoreCase(p.getVendor()) && architecture.equalsIgnoreCase(p.getArchitecture()) ) {
                return p;
            }
        }
        return null;
    }
    
    public static Platform createPlatformFromRootDirectory( Path platformRootPath ) {
        Path platformFilePath = platformRootPath.resolve( PLATFORM_FILENAME );
        return createPlatformFromFile(platformFilePath);
    }
    
    private static Platform createPlatformFromFile( Path platformFilePath ) {
        // Pattern: /home/user/.arduino15/packages/{vendor}/hardware/{architecture}/x.x.x/platform.txt
        int hardwareIndex = -1;
        for ( int i=platformFilePath.getNameCount()-1; i>=0; i-- ) {
            if ( "hardware".equalsIgnoreCase( platformFilePath.getName(i).toString() ) ) {
                hardwareIndex = i;
                break;
            }
        }
        String vendor = platformFilePath.getName(hardwareIndex-1).toString();
        String architecture = platformFilePath.getName(hardwareIndex+1).toString();
        String displayName = "";
        
        try {
            Optional <String> opt = Files.lines(platformFilePath).filter( line -> line.replaceAll("\\s+","").startsWith("name=") ).findAny();
            if ( opt.isPresent() ) {
                displayName = opt.get().split("=")[1];
            }
        } catch (IOException ex) {
            LOGGER.log( Level.WARNING, "Failed to find platform display name in file: " + platformFilePath.toString(), ex );
        }
        
        return new Platform(vendor, architecture, displayName, platformFilePath.getParent());
    }

    public static boolean isValidPlatformRootPath( Path rootPath  ) {
        return rootPath != null && Files.exists( rootPath.resolve(PLATFORM_FILENAME) );
    }
    
    private static Path validateArduinoSettingsPath( Path settingsPath ) throws FileNotFoundException {
        if ( settingsPath == null ) {
            LOGGER.severe( "Failed to find the Arduino settings directory!" );
            throw new FileNotFoundException("Failed to find the Arduino settings directory!");
        }
        return settingsPath;
    }
    


    private final Platform platform;
    
    public BoardConfigNavigator( Platform platform ) {
        this.platform = platform;
    }

    public Platform getPlatform() {
        return platform;
    }
    
    // TODO: Refactor. Move the core-searching logic to ArduinoConfig class
    public Path getSourceCoreDirectoryPath( String boardId ) {
        try {
            Optional <String> coreOpt = parseCore(boardId);
            if ( coreOpt.isPresent() ) {
                String coreValue = coreOpt.get();
                String[] coreTokens = coreValue.split(":");
                if ( coreTokens.length > 1 ) {
                    String vendor = coreTokens[0];
                    String core = coreTokens[1];
                    return ArduinoConfig.getInstance().findInPreferences( line -> {
                        return line.split("=")[0].trim().endsWith("hardwarepath");
                    }).flatMap( hardwarePath -> { 
                        Path vendorPath = Paths.get( hardwarePath, vendor );
                        try {
                            return Files.walk( vendorPath )
                                .filter( 
                                    f -> Files.isDirectory(f)
                                    && f.getFileName().toString().equalsIgnoreCase(core)
                                    && f.getParent().getFileName().toString().equalsIgnoreCase("cores")
                                )
                                .findAny();                            
                        } catch (IOException ex) {
                            LOGGER.log( Level.WARNING, "Failed to find the core directory for " + coreValue, ex );
                            return Optional.empty();
                        }
                    }).get();
                } else {
                    Path coresDirPath = getPlatformRootPath().resolve("cores").resolve(coreValue);
                    if ( Files.exists(coresDirPath) ) {
                        return coresDirPath;
                    } else {
                        LOGGER.log(Level.SEVERE, "Failed to find any core directory under: {0}", coresDirPath);
                        return null;
                    }
                }                
            } else {
                LOGGER.log(Level.SEVERE, "Failed to find source core directory for: {0}", boardId);
            }            
        } catch ( IOException ex ) {
            LOGGER.log( Level.SEVERE, "Failed to find source core directory for: " + boardId, ex );
        }
        return null;
    }

    public List<String> getCurrentlyUnsupportedBoardIDs() {
        return CURRENTLY_UNSUPPORTED_BOARD_IDS;
    }

    public String getProgrammerFilename() {
        return "pic32prog";
    }
    
    public Path getProgrammerPath() {
        Path p = getPlatformRootPath();
        for ( int i=0; i<getPlatformRootPath().getNameCount(); i++ ) {
            Path toolsDirPath = p.resolve("tools");
            if ( Files.exists(toolsDirPath) ) {
                return findProgrammerPath(toolsDirPath);
            }                
            p = p.getParent();
        }
        LOGGER.log( Level.WARNING, "Failed to find the programmer" );
        return null;
    }
    
    public Path getBoardsFilePath() {
        return getPlatformRootPath().resolve( BOARDS_FILENAME );
    }

    public Path getPlatformRootPath() {
        return platform.getRootPath();
    }
        
    public Path getPlatformFilePath() {
        return getPlatformRootPath().resolve( PLATFORM_FILENAME );
    }
    
    public String getFullyQualifiedBoardName( String boardId ) throws IOException {
        // FQBN arduino:avr:nano - [vendor name]:[architecture name]:[boardId].
        return platform.getVendor() + ":" + platform.getArchitecture() + ":" + boardId;
    }
    
    public Path getVariantBoardsFilePath( String boardId ) {
        Path variantDirPath = getVariantPath(boardId);
        if ( variantDirPath == null || !Files.exists(variantDirPath) ) return null;
        
        Path boardsFilePath = variantDirPath.resolve( BOARDS_FILENAME );
        if ( !Files.exists( boardsFilePath ) ) {
            return null;
        }
        
        return boardsFilePath;
    }
    
    public Optional <String> getVariantName( String boardId ) {
        if ( boardId == null ) throw new IllegalArgumentException("Board ID cannot be null!");
        Path mainBoardFilePath = getPlatformRootPath().resolve( BOARDS_FILENAME );
        String variantPathKey = boardId+".build.variant";
        try {
            return Files.lines(mainBoardFilePath)
                .map( line -> line.trim() )
                .filter( line -> !line.startsWith("#") )
                .filter( line -> line.startsWith(variantPathKey) )
                .map( line -> {                        
                    String[] tokens = line.split("=");
                    // Make sure that variantPathKey is the whole key and not just some part of it
                    return ( tokens.length == 2 && variantPathKey.equals(tokens[0]) ) ? tokens[1] : null;
                })
                .filter( value -> value != null )
                .findAny();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public Path getVariantPath( String boardId ) {
        if ( boardId == null ) throw new IllegalArgumentException("Board ID cannot be null!");
        
        try {
            Optional <String> opt = getVariantName(boardId);
            Path variantsDirPath = getPlatformRootPath().resolve( VARIANTS_DIRNAME );
            if ( opt.isPresent() ) {
                Path variantPath = variantsDirPath.resolve( opt.get() );
                // If the path does not exist, it might just be because of the letter casing in the variant name 
                // so go through all directories and compare their lower-case names with lower-case variant name:
                if ( !Files.exists( variantPath ) ) {
                    String lowerCaseDirName = variantPath.getFileName().toString().toLowerCase();
                    Optional<Path> findAny = Files.list( variantsDirPath ).filter( p -> p.getFileName().toString().toLowerCase().equals( lowerCaseDirName ) ).findAny();
                    if ( findAny.isPresent() ) {
                        variantPath = findAny.get();
                    } else {
                        throw new IllegalArgumentException("Did not find any variant directory for board \"" + boardId + "\"");
                    }
                }                
                return variantPath;
            } else {
                throw new IllegalArgumentException("Did not find any variant directory for board \"" + boardId + "\"");
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Map<String,String> parseBoardNamesToIDsLookup() throws IOException {
        Map <String,String> ret = new HashMap<>();
        Path boardsFilePath = getPlatformRootPath().resolve( BOARDS_FILENAME );
        Files.lines(boardsFilePath).forEach( line -> {
            if ( !line.startsWith("#") && line.contains(".name") ) {
                // e.g: cerebot32mx4.name=Cerebot 32MX4
                String[] pair = line.split("\\.name");
                String id = pair[0].trim();
                if ( getCurrentlyUnsupportedBoardIDs().contains(id) ) return;
                ret.put( pair[1].substring(1).trim(), id );
            }
        });
        return ret;
    }
    
    public Set<String> parseBoardIDs() {
        try {
            Path boardFilePath = getPlatformRootPath().resolve( BOARDS_FILENAME );
            return Files.lines(boardFilePath)
                .filter( line -> !line.isEmpty() && !line.trim().startsWith("#") && line.contains(".name") )
                .map( line -> line.substring(0, line.indexOf(".")) ).collect( Collectors.toSet() );            
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public Optional <String> parseMCU( String boardId ) throws IOException {
        return parseConfigValue( boardId + ".build.mcu" );
    }
    
    public Optional <String> parseCore( String boardId ) throws IOException {
        return parseConfigValue( boardId + ".build.core" );
    }
    
    public Optional<String> parseConfigValue( String key ) throws IOException {
        Path boardsFilePath = getPlatformRootPath().resolve( BOARDS_FILENAME );
        return Files.lines(boardsFilePath)
            .filter( line -> !line.startsWith("#") && line.contains(key) )
            .findFirst()
            .map( line -> line.substring( line.indexOf("=")+1 ) );
    }
            
    public BoardConfig readCompleteBoardConfig(String boardId, Path coreDirPath, Path variantDirPath, Path ldScriptDirPath ) throws IOException {
        Map<String, String> data = readCompleteBoardConfigToMap(boardId, coreDirPath, variantDirPath, ldScriptDirPath);
        switch (platform.getArchitecture().toLowerCase()) {
            case "pic32":
                return new ChipKitBoardConfig(platform, data);
            case "samd":
                return new SAMDBoardConfig(platform, data);
            default:
                return new BoardConfig(platform, data);
        }
    }
    
    protected Map <String,String> readCompleteBoardConfigToMap( String boardId, Path coreDirPath, Path variantDirPath, Path ldScriptDirPath ) throws IOException {
        Map <String,String> config = new HashMap<>();
        config.put("runtime.ide.version", "10802");
        config.put("build.core.path", coreDirPath.toString() );
        config.put("build.variant.path", variantDirPath != null ? variantDirPath.toString() : "" );
        config.put("build.ldscript_dir.path", ldScriptDirPath != null ? ldScriptDirPath.toString() : "" );
        config.put("fqbn", getFullyQualifiedBoardName(boardId));
        config.put("build.arch", platform.getArchitecture().toUpperCase() );

        readPlatformFile( getPlatformFilePath(), config );
        readBoardsFile( getBoardsFilePath(), boardId, config );
        readBoardsFile( getVariantBoardsFilePath(boardId), boardId, config );
        
        Pattern tokenPattern = Pattern.compile("(\\{[\\.|\\w|\\-|_]*\\})");
        config.entrySet().forEach( e -> {
            Matcher m = tokenPattern.matcher(e.getValue());
            String newValue = e.getValue();
            while ( m.find() ) {
                String tokenWithBraces = m.group(1);
                String token = tokenWithBraces.substring(1, tokenWithBraces.length()-1);
                String tokenValue = config.get( token );
                if ( tokenValue != null ) {
                    newValue = newValue.replace( tokenWithBraces, tokenValue );
                }
            }
            config.put( e.getKey(), newValue );
        });
        
        String ldScript = config.get("ldscript");
        if ( ldScript != null ) {
            String ldScriptDebug = ldScript.substring( 0, ldScript.lastIndexOf(".") ) + "-debug.ld";
            config.put("ldscript-debug", ldScriptDebug);
        }
        
        return config;
    }
    
    
    // ************************************************************
    // ********************* PRIVATE METHODS **********************
    // ************************************************************    
    private void readPlatformFile( Path configFilePath, Map <String,String> config ) throws IOException {
        LOGGER.log(Level.INFO, "Reading platform file from  \"{0}\".", configFilePath);
        Files.lines(configFilePath).forEach( (line) -> {            
            String[] pair = splitLineToKeyValuePair(line);
            if ( pair == null ) return;
            config.put(pair[0], pair[1]);
        });
    }
    
    private void readBoardsFile( Path configFilePath, String boardId, Map <String,String> config ) throws IOException {
        if ( configFilePath == null ) return;
        LOGGER.log(Level.INFO, "Reading boards file for board \"{0}\" from  \"{1}\".", new Object[]{boardId, configFilePath});
        String keyStart = boardId+".";
        Files.lines(configFilePath).forEach( (line) -> {           
            String[] pair = splitLineToKeyValuePair(line);            
            if ( pair == null ) return;
            if ( !pair[0].startsWith( keyStart ) ) return;
            pair[0] = pair[0].substring(pair[0].indexOf(".")+1);
            config.put(pair[0], pair[1]);
        });
    }
    
    private String[] splitLineToKeyValuePair( String line ) {
        line = line.trim();
        if ( line.isEmpty() || line.startsWith("#") ) {
            return null;
        }
        int separatorIndex = line.indexOf("=");
        String key = line.substring(0, separatorIndex);
        String value = separatorIndex < line.length()-1 ? line.substring(separatorIndex+1) : "";
        return new String[] {key.trim(), value.trim()};
    }
        
    private Path findProgrammerPath( Path startDir ) {
        String progFilename = getProgrammerFilename();
        try {            
            Optional<Path> opt = Files.walk( startDir )
                .filter( f -> !Files.isDirectory(f) && f.getFileName().toString().startsWith(progFilename) && Files.isExecutable(f) )
                .findAny();
            if ( opt.isPresent() ) {
                return opt.get();
            } else {
                LOGGER.log(Level.WARNING, "Failed to find {0} in {1}", new Object[] {progFilename, startDir} );
            }
        } catch (IOException ex) {
            LOGGER.log( Level.WARNING, "Failed to find " + progFilename + " in " + startDir, ex );
        }
        return null;
    }
    
}
