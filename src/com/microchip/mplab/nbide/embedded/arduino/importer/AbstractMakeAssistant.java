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
import com.microchip.mplab.nbide.embedded.arduino.utils.DeletingFileVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class AbstractMakeAssistant {

    
    private static final Logger LOGGER = Logger.getLogger(AbstractMakeAssistant.class.getName());

    protected static final String TOOLS_DIR = "TOOLS_DIR";

    private List<String> compilationCommands;
    private List <String> makefileContents;
    private List <String> objectFilenames;
    
    
    
    public List<String> getCompilationCommands() {
        return new ArrayList<>(compilationCommands);
    }

    public List<String> getMakefileContents() {
        return makefileContents;
    }

    public List<String> getObjectFilenames() {
        return objectFilenames;
    }

    public Path getMakefilePath() {
        return getBuildDirPath().resolve( getMakefileName() );
    }    

    public abstract Path getBuildDirPath();

    public abstract BoardConfig getBoardConfig();

    public abstract String getMakefileName();

    public abstract String getTargetName();

    public abstract GCCToolFinder getToolFinder();

    public void cleanup() {
        try {
            Files.walkFileTree(getBuildDirPath(), new DeletingFileVisitor());
        } catch (IOException ex) {
            LOGGER.log( Level.WARNING, "Exception caught while removing the build directory", ex );
        }
    }

    protected void build() throws IOException, InterruptedException {
        build(null, null);
    }
    
    protected void build( Consumer<String> messageConsumer, Consumer<String> errorConsumer ) throws IOException, InterruptedException {        
        checkPrerequisites();        
        generateMakefile();
        writeMakefile();
        invokeMakeTool( messageConsumer, errorConsumer );
    }
    
    protected void checkPrerequisites() {
        if ( getBuildDirPath() == null ) throw new IllegalStateException("Build Directory Path cannot be null!");
        if ( getMakefileName() == null ) throw new IllegalStateException("Makefile Name cannot be null!");
        if ( getToolFinder() == null ) throw new IllegalStateException("Tool Finder cannot be null!");
        if ( getBoardConfig() == null ) throw new IllegalStateException("Board Config cannot be null!");
        if ( getTargetName() == null ) throw new IllegalStateException("Target Name cannot be null!");        
    }
    
    protected void generateMakefile() throws IOException {
        BoardConfig config = getBoardConfig();
        Path compilerPath = getToolFinder().findTool( LanguageTool.CCCompiler );
        
        makefileContents = new ArrayList<>();
        objectFilenames = new ArrayList<>();
        makefileContents.add( TOOLS_DIR + "=" + compilerPath.getParent().toString() );
        makefileContents.add( getTargetName() + ":" );
        
        // Add variant and core source file paths:
        List <Path> allSourceFiles = getSourceFilePaths(config);
        
        // Generete compilation commands:
        allSourceFiles.forEach(sourceFilePath -> {                
            String sourceFileName = sourceFilePath.getFileName().toString();
            String targetFileName = sourceFileName + ".o";
            objectFilenames.add( targetFileName );
            StringBuilder command = new StringBuilder("\t");

            appendCompilerInvocation(command, compilerPath);
            appendLanguageSpecificOptions(command, config, sourceFilePath);
            appendProcessorOptions(command, config);
            appendDependencies(command, config, sourceFilePath);
            appendMacros(command, config);
            appendSourceFilePath(command, sourceFilePath );
            appendTargetFilePath(command, sourceFilePath.getParent().resolve(targetFileName) );
            
            makefileContents.add( command.toString() );
        });                
    }
    
    protected void appendCompilerInvocation( StringBuilder command, Path compilerPath ) {
        command.append("\"${").append(TOOLS_DIR).append("}").append("/").append(compilerPath.getFileName().toString()).append("\"");
        command.append(" -c");
        command.append( " " );
    }
    
    protected void appendLanguageSpecificOptions( StringBuilder command, BoardConfig config, Path sourceFilePath ) {
        String sourceFileName = sourceFilePath.getFileName().toString();
        if (sourceFileName.endsWith(".S")) {
            command.append( String.join(" ", config.getExtraOptionsAS() ) );
            command.append(" -O1");
        } else if (sourceFileName.endsWith(".c")) {
            command.append(" -g");
            command.append(" -x");
            command.append(" c");
            command.append(" -w");
            command.append(" -O1");
            command.append( " " );
            command.append( String.join(" ", config.getCompilerWarnings()) );
            command.append( " " );
            command.append( String.join(" ", config.getExtraOptionsC()) );
        } else if (sourceFileName.endsWith(".cpp")) {
            command.append(" -g");
            command.append(" -x");
            command.append(" c++");
            command.append(" -w");
            command.append(" -O1");
            command.append( " " );
            command.append( String.join(" ", config.getCompilerWarnings()) );
            command.append( " " );
            command.append( String.join( " ", config.getExtraOptionsCPP() ) );
        }
    }
    
    protected void appendProcessorOptions( StringBuilder command, BoardConfig config ) {
        command.append( String.join(" ", config.getProcessorOptions() ) );
    }
    
    protected void appendDependencies( StringBuilder command, BoardConfig config, Path sourceFilePath ) {
        Path variantPath = config.getVariantDirPath();
        Path corePath = config.getCoreDirPath();
        if (variantPath != null && !variantPath.equals(corePath)) {
            command.append(" -I\"").append(variantPath).append("\"");
        }
        command.append(" -I\"").append(corePath).append("\"");
    }
    
    protected void appendMacros( StringBuilder command, BoardConfig config ) {
        command.append( " " ).append( String.join( " ", parseCompilerMacros(config.getCompilerMacros() ) ) );
    }
    
    protected void appendSourceFilePath( StringBuilder command, Path sourceFilePath ) {
        command.append( " \"" ).append( sourceFilePath.toString() ).append( "\"" );
    }
    
    protected void appendTargetFilePath( StringBuilder command, Path targetFilePath ) {
        command.append( " -o \"" ).append( targetFilePath.toString() ).append( "\"" );
    }
    
    protected void writeMakefile() throws IOException {
        Files.write( getMakefilePath(), getMakefileContents() );
    }
    
    protected void invokeMakeTool( Consumer<String> messageConsumer, Consumer<String> errorConsumer ) throws IOException, InterruptedException {
        Path makeToolPath = getToolFinder().findTool( LanguageTool.MakeTool );
        NativeProcessRunner nativeProcessRunner = new NativeProcessRunner(messageConsumer, errorConsumer);
        int result = nativeProcessRunner.runNativeProcess( getBuildDirPath(), makeToolPath.toString(), "V=1", "-f", getMakefilePath().getFileName().toString() );
        if ( result != 0 ) throw new NativeProcessFailureException( "Compilation failed!" );
    }
        
    protected List<Path> getSourceFilePaths( BoardConfig config ) throws IOException {
        return config.getCoreFilePaths();
    }

    protected List<String> parseCompilerMacros(String macros) {
        return Arrays.asList(macros.split(";")).stream().map(m -> "-D" + m).collect(Collectors.toList());
    }

}
