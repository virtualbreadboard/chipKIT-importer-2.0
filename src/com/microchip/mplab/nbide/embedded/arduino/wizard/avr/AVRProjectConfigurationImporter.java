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
import com.microchip.mplab.nbide.embedded.arduino.importer.Board;
import com.microchip.mplab.nbide.embedded.arduino.wizard.ProjectConfigurationImporter;
import com.microchip.mplab.nbide.embedded.makeproject.api.configurations.MakeConfiguration;
import com.microchip.mplab.nbide.embedded.makeproject.api.configurations.MakeConfigurationBook;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class AVRProjectConfigurationImporter extends ProjectConfigurationImporter {

    private static final String DEFAULT_OPTIMIZATION_OPTION = "-O3";
    private static final List<String> SUPPORTED_AVR_DEVICE_NAMES = Arrays.asList("__AVR_ATmega328P__", "__AVR_ATmega168P__");

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
        String ldAppendOptions = getBoard().getValue("build.mcu").map(mcu -> "-mmcu=" + mcu).orElse("");
        String cAppendOptions = String.join(" ", getExtraOptionsC());
       
        getProjectDescriptor().getConfs().getConfigurtions().forEach(c -> {
            MakeConfiguration mc = (MakeConfiguration) c;
            setAuxOptionValue(mc, "AVR-Global", "common-include-directories", includeDirectories);
            setAuxOptionValue(mc, "AVR-Global", "legacy-libc", "false");
            setAuxOptionValue(mc, "AVR-GCC", "preprocessor-macros", preprocessorMacros);
            setAuxOptionValue(mc, "AVR-GCC", "optimization-level", DEFAULT_OPTIMIZATION_OPTION);
            setAuxOptionValue(mc, "AVR-CPP", "preprocessor-macros", preprocessorMacros);
            setAuxOptionValue(mc, "AVR-CPP", "optimization-level", DEFAULT_OPTIMIZATION_OPTION);
            setAuxOptionValue(mc, "AVR-CPP", "exceptions", Boolean.toString(cppExceptions));
            setAuxOptionValue(mc, "AVR-LD", "remove-unused-sections", "true");
            setAuxOptionValue(mc, "AVR-LD", "extra-lib-directories", ProjectImporter.CORE_DIRECTORY_NAME);
            setAuxOptionValue(mc, "AVR-LD", "input-libraries", LibCoreBuilder.LIB_CORE_NAME);
            setAppendixValue(mc, "AVR-GCC", cAppendOptions);
            setAppendixValue(mc, "AVR-CPP", cppAppendOptions);
            setAppendixValue(mc, "AVR-LD", ldAppendOptions);
        });
    }

    @Override
    protected String getCompilerMacros() {
        Board board = getBoard();
        String avrChip = board.getValue("build.mcu")
                .map(mcu -> "_" + mcu.toLowerCase() + "_")
                .flatMap(_mcu_ -> SUPPORTED_AVR_DEVICE_NAMES.stream().filter(dev -> dev.toLowerCase().contains(_mcu_)).findFirst())
                .orElse("");

        return new StringBuilder()
                .append("F_CPU=").append(board.getValue("build.f_cpu").orElse("")).append(";")
                .append("ARDUINO=").append(board.getValue("runtime.ide.version").orElse("")).append(";")
                .append(board.getValue("build.board").orElse("")).append(";")
                .append("IDE=Arduino").append(";")
                .append(avrChip)
                .toString();
    }

}
