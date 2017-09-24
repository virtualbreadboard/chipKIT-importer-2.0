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


import com.microchip.mplab.nbide.embedded.arduino.utils.CopyingFileVisitor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import static java.nio.file.FileVisitResult.CONTINUE;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class ProjectImporter {

    
    private static final Logger LOGGER = Logger.getLogger(ProjectImporter.class.getName());    
    private static final String LIBRARY_EXAMPLES_DIR_NAME = "examples";
    private static final String LIBRARY_TEST_DIR_NAME = "test";
    
    
    public static final String CORE_DIRECTORY_NAME = "imported-core";    
    public static final String LIBRARIES_DIRECTORY_NAME = "imported-libraries";
    public static final String SOURCE_FILES_DIRECTORY_NAME = "source";
    public static final String IMPORTED_PROPERTIES_FILENAME = "imported.properties";    
    public static final List<String> CUSTOM_LD_SCRIPT_BOARD_IDS = Arrays.asList("fubarino_mini_dev", "fubarino_mini", "lenny", "chipkit_Pi_USB_Serial", "chipkit_Pi", "chipkit_DP32", "cmod");
    
    // TODO: Make source filename matching more robust - maybe match everything that is not an .ld file?
    public static final PathMatcher PROJECT_SOURCE_FILE_MATCHER = FileSystems.getDefault().getPathMatcher("glob:*.{c,C,cpp,CPP,s,S,H,h,X,x,INO,ino}");
    public static final PathMatcher LIBRARY_SOURCE_FILE_MATCHER = FileSystems.getDefault().getPathMatcher("glob:*.{c,C,cpp,CPP,s,S,H,h,X,x}");
    public static final PathMatcher LINKER_SCRIPT_MATCHER = FileSystems.getDefault().getPathMatcher("glob:*.ld");
    public static final PathMatcher LIBRARY_DIR_MATCHER = (Path path) -> {
        String filename = path.getFileName().toString();
        return !filename.equals( LIBRARY_EXAMPLES_DIR_NAME ) && !filename.equals( LIBRARY_TEST_DIR_NAME );
    };
    
    
    // R/W properties
    private boolean copyingFiles;
    private Path sourceProjectDirectoryPath;
    private Path targetProjectDirectoryPath;
    private Path customLdScriptsPath;
    private String boardId;    
    private BoardConfigNavigator boardConfigNavigator;    
    private ArduinoBuilderRunner arduinoBuilderRunner;
    private BootloaderPathProvider bootloaderPathProvider;

    // RO properties set during "execute":
    private List <Path> sourceLibraryPaths;
    private BoardConfig config;
    private Path sourceCoreDirPath;
    private Path sourceVariantDirPath;
    private boolean customLdScriptBoard;
    
    // Fixed properties:
    private final List <String> mainLibraryNames = new ArrayList<>();    
    

    public void setCopyingFiles(boolean copyingFiles) {
        this.copyingFiles = copyingFiles;
    }

    public boolean isCopyingFiles() {
        return copyingFiles;
    }

    public void setSourceProjectDirectoryPath(Path sourceProjectDirectoryPath) {
        this.sourceProjectDirectoryPath = sourceProjectDirectoryPath;
    }

    public Path getSourceProjectDirectoryPath() {
        return sourceProjectDirectoryPath;
    }

    public void setTargetProjectDirectoryPath(Path targetProjectDirectoryPath) {
        this.targetProjectDirectoryPath = targetProjectDirectoryPath;
    }

    public Path getTargetProjectDirectoryPath() {
        return targetProjectDirectoryPath;
    }

    public void setBoardConfigNavigator( BoardConfigNavigator boardConfigNavigator ) {
        this.boardConfigNavigator = boardConfigNavigator;
    }

    public BoardConfigNavigator getBoardConfigNavigator() {
        return boardConfigNavigator;
    }

    public void setArduinoBuilderRunner(ArduinoBuilderRunner arduinoBuilderRunner) {
        this.arduinoBuilderRunner = arduinoBuilderRunner;
    }

    public ArduinoBuilderRunner getArduinoBuilderRunner() {
        return arduinoBuilderRunner;
    }
    
    public void setBootloaderPathProvider(BootloaderPathProvider bootloaderPathProvider) {
        this.bootloaderPathProvider = bootloaderPathProvider;
    }

    public BootloaderPathProvider getBootloaderPathProvider() {
        return bootloaderPathProvider;
    }

    public void setCustomLdScriptsPath(Path customLdScriptsPath) {
        this.customLdScriptsPath = customLdScriptsPath;
    }

    public Path getCustomLdScriptsPath() {
        return customLdScriptsPath;
    }
    
    public void setBoardId( String boardId ) {
        this.boardId = boardId;        
    }

    public String getBoardId() {
        return boardId;
    }
    
    public void execute() throws IOException, InterruptedException {
        // TODO: Add a property check
        customLdScriptBoard = CUSTOM_LD_SCRIPT_BOARD_IDS.contains( boardId );
        
        if ( copyingFiles ) {
            LOGGER.log(Level.INFO, "Running in copy-all mode" );
        } else {
            LOGGER.log(Level.INFO, "Running in no-copy mode" );
        }
        
        sourceCoreDirPath = boardConfigNavigator.getCorePath();
        LOGGER.log(Level.INFO, "Using core directory: {0}", new Object[] {sourceCoreDirPath} );
        sourceVariantDirPath = boardConfigNavigator.getVariantPath(boardId);
        LOGGER.log(Level.INFO, "Using variant directory for board \"{0}\": {1}", new Object[] {boardId, sourceVariantDirPath} );
        
        config = boardConfigNavigator.readCompleteBoardConfig( 
            boardId, 
            copyingFiles ? getCoreDirectoryPath() : sourceCoreDirPath, 
            copyingFiles ? getCoreDirectoryPath() : sourceVariantDirPath, 
            customLdScriptBoard ? getCoreDirectoryPath() : null
        );
        
        createProjectDirectoryStructure();
        Path tempSketchPath = preprocessSourceProject();
        importSketchFiles( tempSketchPath );
        
        if ( copyingFiles ) {
            copyCoreFiles();
            copyLibraries();
            copyLinkerScripts();
        } else if ( customLdScriptBoard ) {
            copyLinkerScripts();
        }
        
        copyBootloaderFiles();
        buildLibCore();
        
        if ( copyingFiles ) {
            arduinoBuilderRunner.cleanup();  // Removes the "temp" directory
        }
    }
    
    public String getPreprocessingCommand() {
        return arduinoBuilderRunner.getCommand();
    }

    public Path getPreprocessedSketchDirectoryPath() {
        return arduinoBuilderRunner.getPreprocessedSketchDirPath();
    }

    public Path getSourceFilesDirectoryPath() {
        return targetProjectDirectoryPath.resolve(SOURCE_FILES_DIRECTORY_NAME);
    }

    public Path getCoreDirectoryPath() {
        return targetProjectDirectoryPath.resolve(CORE_DIRECTORY_NAME);
    }
    
    public Path getLibraryDirectoryPath() {
        return targetProjectDirectoryPath.resolve(LIBRARIES_DIRECTORY_NAME);
    }
    
    public BoardConfig getBoardConfig() {
        return config;
    }
    
    public Stream<Path> getLinkerScriptPaths() throws IOException {
        if ( copyingFiles ) {
            Path corePath = getCoreDirectoryPath();
            return Files.list(corePath).filter( (p) -> LINKER_SCRIPT_MATCHER.matches(p.getFileName()) );
        } else {
            String commonLinkerScriptFilename = config.getCommonLinkerScriptFilename();
            String deviceLinkerScriptFilename = config.getDeviceLinkerScriptFilename();
            return Arrays.asList(
                sourceCoreDirPath.resolve( commonLinkerScriptFilename ),
                sourceCoreDirPath.resolve( deviceLinkerScriptFilename )
            ).stream();
        }
    }
    
    public Stream<Path> getSourceFilePaths() throws IOException {
        Path sourceDirPath = copyingFiles ? getSourceFilesDirectoryPath() : getSourceProjectDirectoryPath();
        return Files.list(sourceDirPath).filter( (p) -> PROJECT_SOURCE_FILE_MATCHER.matches(p.getFileName()) );
    }
    
    public Stream<Path> getPreprocessedSourceFilePaths() throws IOException {
        if ( copyingFiles ) {
            return Stream.of((Path) null);
        } else {
            Path sourceDirPath = getPreprocessedSketchDirectoryPath();
            return Files.list(sourceDirPath).filter( (p) -> PROJECT_SOURCE_FILE_MATCHER.matches(p.getFileName()) );
        }
    }
    
    public Stream<Path> getMainLibraryFilePaths() throws IOException {
        return getLibraryFilePaths(true);
    }
    
    public Stream<Path> getMainLibraryDirPaths() throws IOException {
        return getLibraryDirPaths(true);
    }
    
    public Stream<Path> getAuxLibraryFilePaths() throws IOException {
        return getLibraryFilePaths(false);
    }
    
    public Stream<Path> getAuxLibraryDirPaths() throws IOException {
        return getLibraryDirPaths(false);
    }
    
    public Stream<Path> getCoreFilePaths() throws IOException {                
        if ( copyingFiles ) {
            Path coreDirPath = getCoreDirectoryPath();
            return Files.walk(coreDirPath).filter( p -> !Files.isDirectory(p) );
        } else {
            String deviceLinkerScriptFilename = config.getDeviceLinkerScriptFilename();
            Path deviceLinkerScriptPath;
            
            if ( Files.exists( sourceVariantDirPath.resolve( deviceLinkerScriptFilename ) ) ) {
                deviceLinkerScriptPath = sourceVariantDirPath.resolve( deviceLinkerScriptFilename );
            } else {
                deviceLinkerScriptPath = sourceCoreDirPath.resolve( deviceLinkerScriptFilename );
            }
            
            if ( customLdScriptBoard ) {
                Path coreDirPath = getCoreDirectoryPath();
                String debugDeviceLinkerScriptFilename = config.getDeviceDebugLinkerScriptFilename();
                Path debugDeviceLinkerScriptPath = coreDirPath.resolve( debugDeviceLinkerScriptFilename );
                return Stream.concat( createSourceCoreFilesStream(), Stream.of( deviceLinkerScriptPath, debugDeviceLinkerScriptPath ) );
            } else {
                return Stream.concat( createSourceCoreFilesStream(), Stream.of( deviceLinkerScriptPath ) );
            }
        }
    }

    public boolean isCustomLdScriptBoard() {
        return customLdScriptBoard;
    }
    
    public boolean hasBootloaderPath() {
        return !customLdScriptBoard && bootloaderPathProvider.getBootloaderPath(boardId) != null;
    }
    
    public Path getProductionBootloaderPath() {
        if ( customLdScriptBoard ) return null;
        Path sourceBootloaderPath = bootloaderPathProvider.getBootloaderPath(boardId);
        return sourceBootloaderPath != null ? getCoreDirectoryPath().resolve( sourceBootloaderPath.getFileName() ) : null;
    }

    
    /***************************************
     ********** PRIVATE METHODS ************
     ***************************************/        
    private Path preprocessSourceProject() {
        Path inoFilePath = findMainInoFilePath( sourceProjectDirectoryPath );
        if ( copyingFiles ) {
            arduinoBuilderRunner.preprocess(config, inoFilePath);
        } else {
            arduinoBuilderRunner.preprocess(config, inoFilePath, targetProjectDirectoryPath );
        }
        sourceLibraryPaths = arduinoBuilderRunner.getAllLibraryPaths();
        Path sketchDirPath = arduinoBuilderRunner.getPreprocessedSketchDirPath();
        arduinoBuilderRunner.getMainLibraryPaths().forEach( path -> {
            mainLibraryNames.add(path.getFileName().toString());
        });
        return sketchDirPath;
    }
    
    private void createProjectDirectoryStructure() throws IOException {
        if ( copyingFiles ) {
            Files.createDirectory(targetProjectDirectoryPath.resolve(LIBRARIES_DIRECTORY_NAME));
            Files.createDirectory(targetProjectDirectoryPath.resolve(SOURCE_FILES_DIRECTORY_NAME));
        }
        Files.createDirectory(targetProjectDirectoryPath.resolve(CORE_DIRECTORY_NAME));        
    }
    
    private Stream<Path> getLibraryFilePaths( boolean main ) throws IOException {
        return getLibraryDirPaths(main).flatMap( libDirPath -> {
            try {                
                return Files.walk(libDirPath).filter( p -> LIBRARY_SOURCE_FILE_MATCHER.matches(p.getFileName()) );
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        } );
    }
    
    private Stream<Path> getLibraryDirPaths( boolean main ) throws IOException {
        Stream <Path> libStream = copyingFiles ? Files.list( getLibraryDirectoryPath() ) : sourceLibraryPaths.stream();
        return libStream
            .filter( p -> Files.isDirectory(p) )
            .filter( p -> main == mainLibraryNames.contains(p.getFileName().toString()) );
    }

    private Path findMainInoFilePath( Path inoProjectPath ) {
        return inoProjectPath.resolve(inoProjectPath.getFileName() + ".ino");
    }
            
    private void copyCoreFiles() throws IOException {
        if ( !copyingFiles ) return;
        Path targetCoreDirPath = getCoreDirectoryPath();        
        Files.walkFileTree(sourceCoreDirPath, new CopyingFileVisitor(sourceCoreDirPath, targetCoreDirPath, PROJECT_SOURCE_FILE_MATCHER ));
        Files.walkFileTree(sourceVariantDirPath, new CopyingFileVisitor(sourceVariantDirPath, targetCoreDirPath, PROJECT_SOURCE_FILE_MATCHER ));
    }
    
    private void copyLibraries() {
        if ( !copyingFiles ) return;
        Path targetLibrariesDirPath = getLibraryDirectoryPath();
        sourceLibraryPaths.forEach( libraryPath -> {
            String libName = libraryPath.getFileName().toString().trim();
            if ( libName.isEmpty() ) return;
            try {
                Files.walkFileTree(
                    libraryPath, 
                    new CopyingFileVisitor(
                        libraryPath, 
                        targetLibrariesDirPath.resolve( libName ), 
                        LIBRARY_SOURCE_FILE_MATCHER, 
                        LIBRARY_DIR_MATCHER
                    )
                );
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });
    }
    
    private void copyLinkerScripts() throws IOException {
        Path targetDirectoryPath = getCoreDirectoryPath();
        String commonLinkerScriptFilename = config.getCommonLinkerScriptFilename();
        String deviceLinkerScriptFilename = config.getDeviceLinkerScriptFilename();
        
        boolean commonLinkerScriptFilenameAvailable = commonLinkerScriptFilename != null && !commonLinkerScriptFilename.isEmpty();
        boolean deviceLinkerScriptFilenameAvailable = deviceLinkerScriptFilename != null && !deviceLinkerScriptFilename.isEmpty();
        
        if ( !commonLinkerScriptFilenameAvailable && !deviceLinkerScriptFilenameAvailable ) return;
        
        if ( copyingFiles && commonLinkerScriptFilenameAvailable ) {
            Files.copy( sourceCoreDirPath.resolve( commonLinkerScriptFilename ), targetDirectoryPath.resolve( commonLinkerScriptFilename ) );
        }
        
        if ( customLdScriptBoard ) {
            Path boardCustomLdScriptDirPath = customLdScriptsPath.resolve( sourceVariantDirPath.getFileName() );
            Optional<Path> opt = Files.list(boardCustomLdScriptDirPath).findFirst();
            if ( opt.isPresent() ) {
                Path boardCustomLdScriptPath = opt.get();
                Files.copy( boardCustomLdScriptPath, targetDirectoryPath.resolve( boardCustomLdScriptPath.getFileName() ) );
            } else {
                LOGGER.log(Level.WARNING, "No custom .ld script found for board: {0}", boardId);
            }
        } 
        
        if ( deviceLinkerScriptFilenameAvailable ) {
            if ( copyingFiles && Files.exists( sourceVariantDirPath.resolve( deviceLinkerScriptFilename ) ) ) {
                Files.copy( sourceVariantDirPath.resolve( deviceLinkerScriptFilename ), targetDirectoryPath.resolve( deviceLinkerScriptFilename ) );
            } else if ( copyingFiles ) {
                Files.copy( sourceCoreDirPath.resolve( deviceLinkerScriptFilename ), targetDirectoryPath.resolve( deviceLinkerScriptFilename ) );
            }
        }
    }
    
    private void copyBootloaderFiles() throws IOException {
        // Don't copy bootloader files for boards with custom .ld scripts
        if ( customLdScriptBoard ) return;
        
        // Production bootloader
        Path srcProdBootloaderPath = bootloaderPathProvider.getBootloaderPath(boardId);        
        if ( srcProdBootloaderPath == null ) {
            LOGGER.log(Level.WARNING, "No bootloader .hex file found for board: {0}", boardId);
            return;
        }
        Files.copy(srcProdBootloaderPath, getCoreDirectoryPath().resolve( srcProdBootloaderPath.getFileName() ) );
        
        // Debug bootloader (if exists)
        String prodBootloaderFilename = srcProdBootloaderPath.getFileName().toString();
        String debugBootloaderFilename = convertProdToDebugBootloaderFileName( prodBootloaderFilename );
        Path srcDebugBootloaderPath = srcProdBootloaderPath.getParent().resolve( debugBootloaderFilename );
        if ( Files.exists(srcDebugBootloaderPath) ) {
            Files.copy(srcDebugBootloaderPath, getCoreDirectoryPath().resolve( srcDebugBootloaderPath.getFileName() ) );
        }
    }
    
    private String convertProdToDebugBootloaderFileName( String prodBootloaderFileName ) {
        return prodBootloaderFileName.substring(0, prodBootloaderFileName.length()-".hex".length()) + ".debug.hex";
    }
    
    private void importSketchFiles( Path sketchDirPath ) throws IOException {
        if ( !copyingFiles ) return;
        Files.walkFileTree(sketchDirPath, new CopyingFileVisitor(sketchDirPath, getSourceFilesDirectoryPath(), PROJECT_SOURCE_FILE_MATCHER) {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if ( file.toString().endsWith(".ino.cpp") ) {
                    String filename = file.getFileName().toString();
                    String newFilename = filename.replace(".ino.cpp", ".cpp");
                    Path targetFilePath = target.resolve( source.relativize( Paths.get(file.getParent().toString(), newFilename) ) );
                    copyFile( file, targetFilePath );
                    try {
                        removeLineDirectives( targetFilePath);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                } else { 
                    copyFile( file, target.resolve( source.relativize(file) ) );
                }
                return CONTINUE;
            }            
        });
    }
    
    private void removeLineDirectives( Path sourceFile ) throws IOException {        
        List <String> filteredLines = Files.lines(sourceFile).filter( line -> !line.startsWith("#line ") ).collect( Collectors.toList() );
        Files.write(sourceFile, filteredLines);
    }
    
    private void buildLibCore() throws IOException, InterruptedException {
        Path coreDirPath = targetProjectDirectoryPath.resolve(CORE_DIRECTORY_NAME);
        LibCoreBuilder libCoreBuilder = new LibCoreBuilder();
        libCoreBuilder.build( config, arduinoBuilderRunner.getToolFinder(), LOGGER::info );
        Files.copy( libCoreBuilder.getLibCorePath(), coreDirPath.resolve( LibCoreBuilder.LIB_CORE_FILENAME ) );
        Files.copy( libCoreBuilder.getMakefilePath(), coreDirPath.resolve( libCoreBuilder.getMakefileName() ) );        
        libCoreBuilder.cleanup();
    }                        
    
    private Stream createSourceCoreFilesStream() throws IOException {
        return Stream.concat(
            Stream.concat( Files.walk(sourceCoreDirPath), Files.walk(sourceVariantDirPath) )
                .filter( p -> !Files.isDirectory(p) && PROJECT_SOURCE_FILE_MATCHER.matches(p.getFileName()) ),
            Stream.of( sourceCoreDirPath.resolve( config.getCommonLinkerScriptFilename() ) )
        );
    }
    
}
