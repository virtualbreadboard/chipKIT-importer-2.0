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

package com.microchip.mplab.nbide.embedded.arduino.wizard.avr;

import com.microchip.mplab.nbide.embedded.arduino.importer.LibCoreBuilder;
import com.microchip.mplab.nbide.embedded.arduino.importer.ProjectImporter;
import com.microchip.mplab.nbide.embedded.arduino.wizard.ProjectConfigurationImporter;
import com.microchip.mplab.nbide.embedded.makeproject.api.configurations.MakeConfiguration;
import com.microchip.mplab.nbide.embedded.makeproject.api.configurations.MakeConfigurationBook;
import java.io.File;
import java.io.IOException;
import java.util.Set;

public final class AVRProjectConfigurationImporter extends ProjectConfigurationImporter {
    
    private static final String DEFAULT_OPTIMIZATION_OPTION = "-O3";
    
    public AVRProjectConfigurationImporter(ProjectImporter importer, boolean copyFiles, MakeConfigurationBook projectDescriptor, File targetProjectDir) {
        super(importer, copyFiles, projectDescriptor, targetProjectDir);
    }
    
    @Override
    public void run() throws IOException {
        Set<String> cppAppendOptionsSet = getExtraOptionsCPP();        
        boolean cppExceptions = !cppAppendOptionsSet.remove("-fno-exceptions");        
        String cppAppendOptions = String.join(" ", cppAppendOptionsSet);
        
        String includeDirectories = assembleIncludeDirectories();
        String preprocessorMacros = getCompilerMacros();        
        String ldAppendOptions = "-L" + ProjectImporter.CORE_DIRECTORY_NAME + ",-l" + LibCoreBuilder.LIB_CORE_NAME;
        String cAppendOptions = String.join(" ", getExtraOptionsC());
        
        getProjectDescriptor().getConfs().getConfigurtions().forEach( c-> {
            MakeConfiguration mc = (MakeConfiguration) c;
            setAuxOptionValue(mc, "AVR-Global", "common-include-directories", includeDirectories );
            setAuxOptionValue(mc, "AVR-Global", "legacy-libc", "false");
            setAuxOptionValue(mc, "AVR-GCC", "preprocessor-macros", preprocessorMacros);
            setAuxOptionValue(mc, "AVR-GCC", "optimization-level", DEFAULT_OPTIMIZATION_OPTION );
            setAuxOptionValue(mc, "AVR-CPP", "preprocessor-macros", preprocessorMacros);
            setAuxOptionValue(mc, "AVR-CPP", "optimization-level", DEFAULT_OPTIMIZATION_OPTION );
            setAuxOptionValue(mc, "AVR-CPP", "exceptions", Boolean.toString(cppExceptions));            
            setAuxOptionValue(mc, "AVR-LD", "remove-unused-sections", "true");
            setAppendixValue(mc, "AVR-GCC", cAppendOptions);
            setAppendixValue(mc, "AVR-CPP", cppAppendOptions);
            setAppendixValue(mc, "AVR-LD", ldAppendOptions);
        });
    }
    
    
}
