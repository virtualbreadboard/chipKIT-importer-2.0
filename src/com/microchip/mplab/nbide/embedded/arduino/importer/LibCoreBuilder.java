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

import com.microchip.mplab.nbide.embedded.api.LanguageTool;
import com.microchip.mplab.nbide.embedded.arduino.importer.drafts.Board;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


public class LibCoreBuilder extends AbstractMakeAssistant {

    
    public static final String LIB_CORE_NAME = "Core";
    public static final String LIB_CORE_FILENAME = "lib" + LIB_CORE_NAME + ".a";
    
    private Path buildDirPath;
    private BoardConfig boardConfig;
    private Board board;
    private GCCToolFinder toolFinder;
    private Path libCorePath;
    private String archiveCommand;
    

    public LibCoreBuilder() {        
        
    }

    @Override
    public String getMakefileName() {
        return "Makefile-" + LIB_CORE_NAME;
    }

    public String getArchiveCommand() {
        return archiveCommand;
    }

    public Path getLibCorePath() {
        return libCorePath;
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
        return LIB_CORE_FILENAME;
    }

    @Override
    public GCCToolFinder getToolFinder() {
        return toolFinder;
    }

    public void build( Path makefilePath, GCCToolFinder toolFinder ) throws IOException, InterruptedException {
        build(makefilePath, toolFinder, null);
    }
    
    public void build( Path makefilePath, GCCToolFinder toolFinder, Consumer<String> messageConsumer ) throws IOException, InterruptedException {
        this.buildDirPath = Files.createTempDirectory("build");
        this.toolFinder = toolFinder;
        this.libCorePath = buildDirPath.resolve(LIB_CORE_FILENAME);
        updateMakefile( makefilePath, toolFinder );
        invokeMakeTool(messageConsumer, messageConsumer);
    }

    public void build(BoardConfig config, GCCToolFinder toolFinder) throws IOException, InterruptedException {
        build(config, toolFinder, null);
    }
    
    public void build(Board board, GCCToolFinder toolFinder, Consumer<String> messageConsumer) throws IOException, InterruptedException {
        this.buildDirPath = Files.createTempDirectory("build");
        this.board = board;
        this.toolFinder = toolFinder;
        this.libCorePath = buildDirPath.resolve(LIB_CORE_FILENAME);
        build( messageConsumer, messageConsumer );
    }
    
    // TODO: Remove
    public void build(BoardConfig boardConfig, GCCToolFinder toolFinder, Consumer<String> messageConsumer) throws IOException, InterruptedException {
        this.buildDirPath = Files.createTempDirectory("build");
        this.boardConfig = boardConfig;
        this.toolFinder = toolFinder;
        this.libCorePath = buildDirPath.resolve(LIB_CORE_FILENAME);
        build( messageConsumer, messageConsumer );
    }
    
    @Override
    protected void generateMakefile() throws IOException {
        super.generateMakefile();
        
        // Generate archiver command:
        Map <String,String> auxData = new HashMap<>();
        auxData.put( "runtime.tools.avr-gcc.path", getToolchainPath().toString() );
        auxData.put( "archive_file_path", LIB_CORE_FILENAME );
        getObjectFilenames().forEach( n -> {
            auxData.put("object_file", n);
            getMakefileContents().add( "\t" + board.getValue("recipe.ar.pattern", auxData).get() );
        });
    } 
    
    @Override
    protected void appendTargetFilePath( StringBuilder command, Path targetFilePath ) {
        command.append( " -o \"" ).append( targetFilePath.getFileName().toString() ).append( "\"" );
    }
    

    //*************************************************
    //*************** PRIVATE METHODS *****************
    //*************************************************
    private static void updateMakefile(Path makefilePath, GCCToolFinder toolFinder) throws IOException {
        Path compilerPath = toolFinder.findTool( LanguageTool.CCCompiler );
        List<String> makefileLines = Files.readAllLines(makefilePath);
        for ( int i=0; i<makefileLines.size(); i++ ) {
            String line = makefileLines.get(i).trim();
            if ( line.startsWith( TOOLS_DIR ) ) {
                makefileLines.set( i, TOOLS_DIR + "=" + compilerPath.getParent().toString() );
                break;
            }
        }
        Files.write(makefilePath, makefileLines);
    }

}