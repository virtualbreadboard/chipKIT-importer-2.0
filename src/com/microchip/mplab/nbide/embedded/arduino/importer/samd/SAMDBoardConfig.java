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

package com.microchip.mplab.nbide.embedded.arduino.importer.samd;


import com.microchip.mplab.nbide.embedded.arduino.importer.BoardConfig;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class SAMDBoardConfig extends BoardConfig {

    /* 
     * TODO:
     *
     * Add the following paths to included directories:
     *      "/home/gregor/.arduino15/packages/arduino/tools/CMSIS/4.5.0/CMSIS/Include/" 
     *      "/home/gregor/.arduino15/packages/arduino/tools/CMSIS-Atmel/1.1.0/CMSIS/Device/ATMEL/"
     *
     */

    public SAMDBoardConfig( Map <String,String> data ) {        
        super( data );
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
    public Set <String> getProcessorOptions() {
        // Ignore
        return new HashSet<>();
    }

}
