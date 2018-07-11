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

import com.microchip.crownking.opt.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.Utilities;

public abstract class ArduinoConfig {
    
    
    public static final String ROOT_PLATFORM_VENDOR = "arduino";
    public static final String ROOT_PLATFORM_ARCH = "avr";
    
    private static final Logger LOGGER = Logger.getLogger(ArduinoConfig.class.getName());
    private static ArduinoConfig INSTANCE;
    
    public static synchronized ArduinoConfig getInstance() {
        if ( INSTANCE == null ) {
            if ( Utilities.isMac() ){
                INSTANCE = new MacOSXArduinoConfig();
            } else if ( Utilities.isWindows() ) {
                INSTANCE = new Win32ArduinoConfig();
            } else {
                INSTANCE = new LinuxArduinoConfig();
            }
        }
        return INSTANCE;
    }
    
    public abstract Path getSettingsPath();
    public abstract Path getSketchPath();        
    public abstract Path findArduinoBuilderPath( Path arduinoInstallPath );
    
    // TODO: Change return type to Optional
    public Path findUserLibrariesPath() {
        return getSketchPath().resolve("libraries");
    }
        
    // TODO: Change return type to Optional
    public Path getPackagesPath() {
        return getSettingsPath().resolve("packages");
    }
    
    // TODO: Change return type to Optional
    public Path getPlatformRootPath( Platform platform ) {
        return getPackagesPath().resolve( platform.getVendor() );
    }
    
    public boolean isValidArduinoInstallPath(Path path) {
        Path p = findArduinoBuilderPath(path);
        return Files.exists(p);
    }

    public Optional<Path> findHardwarePath() {
        return findInPreferences( line -> line.split("=")[0].trim().endsWith("hardwarepath") ).map( Paths::get );
    }
    
    public Optional <String> findInPreferences( Predicate<String> predicate ) {
        Path preferencesPath = getSettingsPath().resolve("preferences.txt");
        try {
            return Files.lines(preferencesPath)
                .filter(line -> !line.trim().startsWith("#"))
                .filter(predicate)
                .findFirst()
                .map(line -> {
                    String[] tokens = line.split("=");
                    return (tokens.length > 1) ? tokens[1].trim() : null;
                });
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to read the preferences.txt file", ex);
            return Optional.empty();
        }
    }
    
    public Optional <Path> getDefaultArduinoPlatformPath() {
        return findInPreferences( line -> line.split("=")[0].trim().endsWith("hardwarepath") )
            .map( hardwarePath -> Paths.get( hardwarePath, ROOT_PLATFORM_VENDOR, ROOT_PLATFORM_ARCH ) );
    }

    public Optional<Path> findToolsPath() {
        return findHardwarePath().map( hardwarePath -> hardwarePath.resolve("tools").resolve(ROOT_PLATFORM_ARCH) );
    }
    
    // TODO: Change return type to Optional
    public Path findToolsBuilderPath(Path arduinoInstallPath) {
        return arduinoInstallPath.resolve("tools-builder");
    }

    // TODO: Change return type to Optional
    public Path findBuiltInLibrariesPath(Path arduinoInstallPath) {
        return arduinoInstallPath.resolve("libraries");
    }
    
    // TODO: Add more error-resilient Arduino version parsing
    public Version findCurrentVersion(Path arduinoInstallPath) throws IOException {
        Path revisionsFilePath = Paths.get(arduinoInstallPath.toString(), "revisions.txt");
        Optional<String> opt = Files.lines(revisionsFilePath).filter( line -> line.trim().startsWith("ARDUINO") ).findFirst();
        if ( opt.isPresent() ) {
            String line = opt.get();
            String[] tokens = line.split(" ");
            String versionString = tokens[1];
            if ( versionString.contains(".") ) {
                return new Version(versionString);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
    
    public boolean isCurrentVersionValid(Path arduinoInstallPath, Version minimumValidVersion) {
        if ( minimumValidVersion == null ) throw new IllegalArgumentException("minimumValidVersion cannot be null");
        Version currentVersion = null;
        try {
            currentVersion = findCurrentVersion(arduinoInstallPath);
        } catch (IOException ex) {
            LOGGER.log( Level.SEVERE, "Failed to find Arduino version", ex );
        }
        if ( currentVersion != null ) {
            return currentVersion.compareTo( minimumValidVersion ) >= 0;
        } else {
            return false;
        }
    }
    
}

