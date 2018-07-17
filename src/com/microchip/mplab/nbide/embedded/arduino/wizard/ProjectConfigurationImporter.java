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

package com.microchip.mplab.nbide.embedded.arduino.wizard;

import com.microchip.mplab.nbide.embedded.arduino.importer.ProjectImporter;
import com.microchip.mplab.nbide.embedded.arduino.importer.Board;
import com.microchip.mplab.nbide.embedded.makeproject.api.configurations.MakeConfiguration;
import com.microchip.mplab.nbide.embedded.makeproject.api.configurations.MakeConfigurationBook;
import com.microchip.mplab.nbide.embedded.makeproject.api.configurations.OptionConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ProjectConfigurationImporter {
    
    private final Board board;
    private final ProjectImporter importer;
    private final boolean copyFiles;
    private final MakeConfigurationBook projectDescriptor;
    private final File targetProjectDir;

    protected ProjectConfigurationImporter(ProjectImporter importer, boolean copyFiles, MakeConfigurationBook projectDescriptor, File targetProjectDir) {
        this.importer = importer;
        this.board = importer.getBoard();
        this.copyFiles = copyFiles;
        this.projectDescriptor = projectDescriptor;
        this.targetProjectDir = targetProjectDir;
    }

    protected Board getBoard() {
        return board;
    }

    protected ProjectImporter getImporter() {
        return importer;
    }

    protected MakeConfigurationBook getProjectDescriptor() {
        return projectDescriptor;
    }

    protected File getTargetProjectDir() {
        return targetProjectDir;
    }

    protected boolean isCopyFiles() {
        return copyFiles;
    }
    
    public abstract void run() throws IOException;
    
    protected String assembleIncludeDirectories() throws IOException {
        Path projectPath = Paths.get(targetProjectDir.getAbsolutePath());
        Stream<Path> mainLibraryDirPaths = importer.getMainLibraryDirPaths();
        StringBuilder includesBuilder = new StringBuilder();
        if (copyFiles) {
            includesBuilder.append(ProjectImporter.CORE_DIRECTORY_NAME);
        } else {
            List <Path> coreDirPaths = board.getCoreDirPaths();
            for ( int i=0; i<coreDirPaths.size(); i++ ) {                
                if ( i>0 ) includesBuilder.append(";");
                includesBuilder.append( coreDirPaths.get(i) );
            }
        }
        mainLibraryDirPaths.forEach(path -> {
            Path srcPath = path.resolve("src");
            if ( Files.exists(srcPath) ) {
                includesBuilder.append(";").append(copyFiles ? projectPath.relativize(srcPath) : srcPath.toAbsolutePath());
            } else {
                includesBuilder.append(";").append(copyFiles ? projectPath.relativize(path) : path.toAbsolutePath());
            }
            Path utilityPath = path.resolve("utility");
            if ( Files.exists(utilityPath) ) {
                includesBuilder.append(";").append(copyFiles ? projectPath.relativize(utilityPath) : utilityPath.toAbsolutePath());
            }
        });
        
        return includesBuilder.toString();
    }
    
    protected void setAuxOptionValue(MakeConfiguration makeConf, String confItemId, String propertyKey, String propertyValue) {        
        OptionConfiguration conf = (OptionConfiguration) makeConf.getAuxObject(confItemId);
        if ( conf != null ) {
            conf.setProperty(propertyKey, propertyValue);
            conf.markChanged();
        }
    }

    protected void setAppendixValue(MakeConfiguration makeConf, String confItemId, String value) {                
        OptionConfiguration conf = (OptionConfiguration) makeConf.getAuxObject(confItemId);
        if ( conf != null ) {
            conf.setAppendix(value);
            conf.markChanged();        
        }
    }
    
    protected Set <String> getExtraOptionsC() {
        Set <String> optionSet = new LinkedHashSet<>();
        parseOptions(optionSet, board.getValue("compiler.c.flags"));        
        parseOptions(optionSet, board.getValue("compiler.c.extra_flags"));
        parseOptions(optionSet, board.getValue("build.extra_flags"));
        removeRedundantCompilerOptions(optionSet);
        removeIllegalCharacters(optionSet);
        return optionSet;
    }
    
    protected Set <String> getCompilerWarnings() {
        Set <String> optionSet = new LinkedHashSet<>();
        parseOptions(optionSet, board.getValue("compiler.warning_flags.all"));
        return optionSet;
    }
    
    protected Set <String> getExtraOptionsAS() {
        Set <String> optionSet = new LinkedHashSet<>();
        parseOptions(optionSet, board.getValue("compiler.S.flags"));
        parseOptions(optionSet, board.getValue("compiler.c.extra_flags"));
        parseOptions(optionSet, board.getValue("build.extra_flags"));
        removeRedundantCompilerOptions(optionSet);
        removeIllegalCharacters(optionSet);
        return optionSet;
    }
    
    protected Set <String> getExtraOptionsCPP() {
        Set <String> optionSet = new LinkedHashSet<>();
        parseOptions(optionSet, board.getValue("compiler.cpp.flags"));
        parseOptions(optionSet, board.getValue("compiler.cpp.extra_flags"));        
        parseOptions(optionSet, board.getValue("build.extra_flags"));
        removeRedundantCompilerOptions(optionSet);        
        removeIllegalCharacters(optionSet);
        optionSet.add("-std=gnu++11");
        return optionSet;
    }
    
    protected Set <String> getProcessorOptions() {
        Set <String> optionSet = new LinkedHashSet<>();
        optionSet.add(" -mmcu=" + getMCU() );
        return optionSet;
    }
    
    protected Set <String> getExtraOptionsLD( boolean debug, boolean coreCopied ) {
        return new HashSet<>();
    }
    
    protected String getCompilerMacros() {
        return new StringBuilder()
            .append("F_CPU=").append( board.getValue("build.f_cpu").orElse("") ).append(";")
            .append("ARDUINO=").append( board.getValue("runtime.ide.version").orElse("") ).append(";")
            .append( board.getValue("build.board").orElse("") ).append(";")
            // TODO: Verify whether the two MPxxx parameters are really required
//            .append("MPIDEVER=16777998" ).append(";")
//            .append("MPIDE=150" ).append(";")
            .append("IDE=Arduino" ).append(";")
            .append("XPRJ_default=default" ).append(";")
            .append("__CTYPE_NEWLIB").toString();
    }
    
    protected void removeRedundantCompilerOptions( Set <String> optionSet ) {
        Iterator <String> iter = optionSet.iterator();
        while ( iter.hasNext() ) {
            String option = iter.next();
            if ( option.trim().isEmpty() || option.equals("-g") || option.equals("-c") || option.equals("-w") || option.startsWith("-O") ) {
                iter.remove();
            }
        }
    }
    
    protected void removeIllegalCharacters( Set <String> optionSet ) {
        List <String> fixedItems = optionSet
            .stream()
            .map( option -> option.trim() )
            .map( trimmedOption -> {
                // Remove single quotes like in: '-DUSB_MANUFACTURER="Adafruit"'
                if ( trimmedOption.startsWith("'") ) {
                    trimmedOption = trimmedOption.substring(1);
                }
                if ( trimmedOption.endsWith("'") ) {
                    trimmedOption = trimmedOption.substring(0, trimmedOption.length()-1);
                }
                return trimmedOption;
            }).collect( Collectors.toList() );
        
        optionSet.clear();
        optionSet.addAll(fixedItems);
    }
    
    protected void removeRedundantLinkerOptions( Set <String> optionSet ) {
        Iterator <String> iter = optionSet.iterator();
        while ( iter.hasNext() ) {
            String option = iter.next();
            if ( option.startsWith("-Wl") ) {  // -Wl group will be added later
                iter.remove();
            }
        }
    }
    
    protected void parseOptions( Set <String> optionsSet, Optional<String> optionsString ) {
        optionsString.ifPresent( s -> {
            String[] options = s.split("::|\\s+");
            optionsSet.addAll(Arrays.asList(options));
        });
    }
    
    protected String getMCU() {
        return board.getValue("build.mcu").orElse("");
    }
    
}
