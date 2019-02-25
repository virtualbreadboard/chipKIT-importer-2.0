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

import java.util.Map;

public final class BoardConfigurationFactory {
    
    
    private static final String KEY_BUILD_EXTRA_FLAGS = "build.extra_flags";
    
       
    private BoardConfigurationFactory() {}
    
    public static BoardConfiguration create( Board board, Map<BoardOption, String> boardOptionsToValuesLookup ) {
        
        if ( board.isAVR() ) {
            return createSAMDBoardConfiguration(board, boardOptionsToValuesLookup);
        } else if ( board.isPIC32() ) {
            return createPIC32BoardConfiguration(board, boardOptionsToValuesLookup);
        } else {
            return createAVRBoardConfiguration(board, boardOptionsToValuesLookup);
        }
        
    }
    
    private static BoardConfiguration createSAMDBoardConfiguration(Board board, Map<BoardOption, String> boardOptionsToValuesLookup) {
        return new BoardConfiguration(board, boardOptionsToValuesLookup);
    }

    private static BoardConfiguration createPIC32BoardConfiguration(Board board, Map<BoardOption, String> boardOptionsToValuesLookup) {
        BoardConfiguration boardConfiguration = new BoardConfiguration(board, boardOptionsToValuesLookup);
        boardConfiguration.putValue( KEY_BUILD_EXTRA_FLAGS, boardConfiguration.getValue(KEY_BUILD_EXTRA_FLAGS).map( flags -> flags + " -D__CTYPE_NEWLIB -mnewlib-libc").orElse("") );
        return boardConfiguration;
    }

    private static BoardConfiguration createAVRBoardConfiguration(Board board, Map<BoardOption, String> boardOptionsToValuesLookup) {
        return new BoardConfiguration(board, boardOptionsToValuesLookup);
    }
    
}
