package com.microchip.mplab.nbide.embedded.arduino.importer;

import java.nio.file.Path;
import java.util.Objects;

public class Platform {
    
    private final String vendor;
    private final String architecture;
    private final String displayName;
    private final Path rootPath;

    public Platform(String vendor, String architecture, String displayName, Path rootPath) {
        this.vendor = vendor;
        this.architecture = architecture;
        this.displayName = displayName;
        this.rootPath = rootPath;
    }

    public String getArchitecture() {
        return architecture;
    }

    public String getVendor() {
        return vendor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Path getRootPath() {
        return rootPath;
    }

    @Override
    public String toString() {
        return vendor + ":" + architecture;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + Objects.hashCode(this.vendor);
        hash = 37 * hash + Objects.hashCode(this.architecture);
        hash = 37 * hash + Objects.hashCode(this.rootPath);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Platform other = (Platform) obj;
        if (!Objects.equals(this.vendor, other.vendor)) {
            return false;
        }
        if (!Objects.equals(this.architecture, other.architecture)) {
            return false;
        }
        if (!Objects.equals(this.rootPath, other.rootPath)) {
            return false;
        }
        return true;
    }

    
    
}
