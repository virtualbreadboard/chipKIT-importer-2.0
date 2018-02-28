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

package regression;

import com.microchip.mplab.nbide.embedded.api.LanguageTool;
import com.microchip.mplab.nbide.embedded.arduino.importer.*;
import static com.microchip.mplab.nbide.embedded.arduino.importer.ProjectImporter.LIBRARIES_DIRECTORY_NAME;
import com.microchip.mplab.nbide.embedded.arduino.importer.drafts.Board;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class ProjectBuilder extends AbstractMakeAssistant {

    
    private static final PathMatcher LIBRARY_SOURCE_FILE_MATCHER = FileSystems.getDefault().getPathMatcher("glob:*.{c,C,cpp,CPP,s,S,X,x}");
    
    private Path buildDirPath;
    private Path projectDirPath;
    private Board board;
    private List<Path> libraryPaths;
    private GCCToolFinder toolFinder;
    

    public ProjectBuilder() {        
        
    }

    @Override
    public String getMakefileName() {
        return "Makefile";
    }

    @Override
    public Path getBuildDirPath() {
        return buildDirPath;
    }

    @Override
    public Board getBoard() {
        return board;
    }
    
    @Override
    public String getTargetName() {
        return "all";
    }

    @Override
    public GCCToolFinder getToolFinder() {
        return toolFinder;
    }
    
    public void build(Path projectDirPath, Path buildDirPath, ProjectImporter importer, Consumer<String> messageConsumer, Consumer<String> errorConsumer) throws IOException, InterruptedException {
        this.projectDirPath = projectDirPath;
        this.buildDirPath = buildDirPath;
        this.board = importer.getBoard();
        this.toolFinder = importer.getArduinoBuilderRunner().getToolFinder();
        this.libraryPaths = Stream.concat(importer.getMainLibraryDirPaths(), importer.getAuxLibraryDirPaths()).collect( Collectors.toList() );
        build(messageConsumer, errorConsumer);
    }
    
    

//    @Override
//    protected void appendDependencies(StringBuilder command, Board config) {
//        super.appendDependencies(command, config); 
//        libraryPaths.forEach( dir ->  {
//            command.append(" -I\"").append(dir.toString()).append("\"");
//        });
//        
//        
////        String sourceDir = projectDirPath.resolve( ProjectImporter.SOURCE_FILES_DIRECTORY_NAME ).toString();
////        Path librariesDirPath = projectDirPath.resolve( LIBRARIES_DIRECTORY_NAME );
////        
////        if ( sourceFilePath.toString().startsWith( sourceDir ) ) {            
////            try {
////                Files.list(librariesDirPath).forEach( dir -> {
////                    command.append(" -I\"").append(dir.toString()).append("\"");
////                });
////            } catch (IOException ex) {
////                throw new RuntimeException(ex);
////            }
////        }
//    }
//
//    @Override
//    protected void appendTargetFilePath(StringBuilder command, Path targetFilePath) {
//        Path buildPath = projectDirPath.resolve( "build" ).resolve("default").resolve("production");
//        Path newTargetFilePath = buildPath.resolve( projectDirPath.relativize( targetFilePath ) );
//        command.append( " -o \"" );
//        try {
//            Files.createDirectories( newTargetFilePath.getParent() );
//            command.append( newTargetFilePath.toString() );
//        } catch (IOException ex) {
//            throw new RuntimeException(ex);
//        }
//        command.append( "\"" );
//    }
    
    @Override
    protected void invokeMakeTool( Consumer<String> messageConsumer, Consumer<String> errorConsumer ) throws IOException, InterruptedException {
        Path makeToolPath = getToolFinder().findTool( LanguageTool.MakeTool );
        NativeProcessRunner nativeProcessRunner = new NativeProcessRunner(messageConsumer, errorConsumer);
        int result = nativeProcessRunner.runNativeProcess( projectDirPath, makeToolPath.toString(), "V=1", "-f", getMakefileName() );
        if ( result != 0 ) throw new NativeProcessFailureException( "Compilation failed! See import.log file for details" );
    }

    @Override
    public Path getMakefilePath() {
        return projectDirPath.resolve( getMakefileName() );
    }

    @Override
    protected List<Path> getSourceFilePaths(Board board) throws IOException {
        Path librariesDirPath = projectDirPath.resolve( LIBRARIES_DIRECTORY_NAME );
        Path sourceFileDir = projectDirPath.resolve( ProjectImporter.SOURCE_FILES_DIRECTORY_NAME );
        String sourceDirName = Files.exists( sourceFileDir ) ? sourceFileDir.getFileName().toString() : "sketch";
        
        // TODO: The librariesDirPath will never be created in the no-copy import mode
        if ( Files.exists( librariesDirPath ) ) {
            return Stream.concat(
                Files.list(librariesDirPath).flatMap( libDirPath -> {
                    try {                
                        return Files.walk(libDirPath).filter( p -> LIBRARY_SOURCE_FILE_MATCHER.matches(p.getFileName()) );
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                } ),
                Files.list( projectDirPath.resolve( sourceDirName ) ) 
            ).collect( Collectors.toList() );
        } else {
            return Files.list( projectDirPath.resolve( sourceDirName ) ).collect( Collectors.toList() );
        }
    }

}