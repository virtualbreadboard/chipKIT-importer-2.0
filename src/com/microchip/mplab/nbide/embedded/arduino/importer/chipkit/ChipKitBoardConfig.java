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

package com.microchip.mplab.nbide.embedded.arduino.importer.chipkit;


import com.microchip.mplab.nbide.embedded.arduino.importer.BoardConfig;
import com.microchip.mplab.nbide.embedded.arduino.importer.Platform;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


public class ChipKitBoardConfig extends BoardConfig {


    public ChipKitBoardConfig( Platform platform, Map <String,String> data ) {        
        super( platform, data );
    }
    
    @Override
    public String getDeviceLinkerScriptFilename() {
        return getRawData().get("ldscript");
    }
    
    @Override
    public String getDeviceDebugLinkerScriptFilename() {
        return getRawData().get("ldscript-debug");
    }

    @Override
    public String getCommonLinkerScriptFilename() {
        return getRawData().get("ldcommon");
    }
    
    @Override
    public Set <String> getExtraOptionsC() {
        Set <String> optionSet = super.getExtraOptionsC();        
        optionSet.add("-mnewlib-libc");
        return optionSet;
    }
    
    @Override
    public Set <String> getExtraOptionsCPP() {
        Set <String> optionSet = super.getExtraOptionsCPP();
        optionSet.add("-mnewlib-libc");
        return optionSet;
    }
    
    @Override
    public Set <String> getProcessorOptions() {
        Set <String> optionSet = new LinkedHashSet<>();
        optionSet.add(" -mprocessor=" + getMCU() );
        return optionSet;
    }
    
    @Override
    public Set <String> getExtraOptionsLD( boolean debug, boolean coreCopied ) {
        Map<String, String> data = getRawData();        
        String chipkitCoreDirectory = data.get("build.core.path");
        String chipkitVariantDirectory = data.get("build.variant.path");
        String ldScriptDirectory = data.get("build.ldscript_dir.path");
        String ldscript = debug ? data.get("ldscript-debug") : data.get("ldscript");
        String ldcommon = data.get("ldcommon");
        Set <String> optionSet = new LinkedHashSet<>();
        parseOptions( optionSet, data.get("compiler.c.elf.flags") );
        removeRedundantCompilerOptions(optionSet);
        removeRedundantLinkerOptions(optionSet);
        optionSet.add("-mnewlib-libc");
        if ( coreCopied ) {
            optionSet.add("-T\"" + ldscript + "\"");
            optionSet.add("-T\"" + ldcommon + "\"");
        } else {
            Path ldcommonPath = Paths.get( chipkitCoreDirectory, ldcommon );
            Path ldscriptPath = Paths.get( debug && !ldScriptDirectory.isEmpty() ? ldScriptDirectory : chipkitCoreDirectory, ldscript );
            if ( !Files.exists(ldscriptPath) && !debug ) {
                ldscriptPath = Paths.get( chipkitVariantDirectory, ldscript );
            }
            optionSet.add("-T\"" + ldscriptPath.toString() + "\"");
            optionSet.add("-T\"" + ldcommonPath.toString() + "\"");
        }
        return optionSet;
    }

}
