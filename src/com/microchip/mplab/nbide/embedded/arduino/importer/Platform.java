package com.microchip.mplab.nbide.embedded.arduino.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Platform extends ArduinoDataSource {
    
    private static final Logger LOGGER = Logger.getLogger(Platform.class.getName());
    
    public static final String PLATFORM_FILENAME = "platform.txt";
    public static final String BOARDS_FILENAME = "boards.txt";    
    public static final String VARIANTS_DIRNAME = "variants";

    private final String vendor;
    private final String architecture;
    private final Path rootPath;
    
    private Map <String,String> boardsData;
    private Map <String,Board> boardLookup = new HashMap<>();

    public Platform(Platform parent, String vendor, String architecture, Path rootPath) throws IOException {
        super( parent );
        this.vendor = vendor;
        this.architecture = architecture;
        this.rootPath = rootPath;
        this.data = parseDataFile( PLATFORM_FILENAME );
    }

    public Platform getParent() {
        return (Platform) parent;
    }

    public String getArchitecture() {
        return architecture;
    }

    public String getVendor() {
        return vendor;
    }

    public boolean isPIC32() {
        return ( architecture != null ) ? architecture.toLowerCase().equals("pic32") : false;
    }
    
    public boolean isAVR() {
        return ( architecture != null ) ? architecture.toLowerCase().equals("avr") : false;
    }
    
    public boolean isSAMD() {
        return ( architecture != null ) ? architecture.toLowerCase().equals("samd") : false;
    }
    
    public Optional<String> getDisplayName() {
        return getValue("name");
    }

    public Path getRootPath() {
        return rootPath;
    }
    
    public Path getBoardsFilePath() {
        return rootPath.resolve( BOARDS_FILENAME );
    }
    
    public Path getPlatformFilePath() {
        return rootPath.resolve( PLATFORM_FILENAME );
    }
    
    public Map<String,String> getBoardNamesToIDsLookup() {
        checkBoardsData();
        return boardsData.entrySet()
            .stream()
            .filter( e -> e.getKey().contains(".name") )
            .collect( Collectors.toMap( e -> e.getValue(), e -> e.getKey().split("\\.")[0] ) );
    }
    
    public Set<String> getBoardIDs() throws IOException {
        checkBoardsData();
        if ( boardLookup.isEmpty() ) {        
            return boardsData.entrySet()
                .stream()
                .filter( e -> e.getKey().contains(".name") )
                .map( e -> e.getKey().split("\\.")[0] )
                .collect( Collectors.toSet() );
        } else {
            return boardLookup.keySet();
        }
    }
    
    public List<Board> getBoards() throws IOException {        
        if ( boardLookup.isEmpty() ) generateBoardLookup();
        return new ArrayList<>(boardLookup.values());
    }
    
    public Board getBoard( String boardId ) throws IOException {
        if ( boardLookup.isEmpty() ) generateBoardLookup();
        return boardLookup.get( boardId );
    }

    @Override
    public String toString() {
        return "Platform{" + "vendor=" + vendor + ", architecture=" + architecture + ", rootPath=" + rootPath + '}';
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


    // ***************************************
    // ********** PRIVATE METHODS ************
    // ***************************************
    private void generateBoardLookup() throws IOException {
        boardLookup = getBoardIDs().stream().map( id -> new Board(this, id, boardsData) ).collect( Collectors.toMap( Board::getBoardId, Function.identity() ) );
    }
    
    private void checkBoardsData() {
        if ( boardsData == null ) try {
            boardsData = parseDataFile( BOARDS_FILENAME );
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to parse boards file", ex);
        }
    }
    
    private Map <String,String> parseDataFile( String filename ) throws IOException {
        try ( Stream <String> lines = Files.lines(rootPath.resolve( filename )) ) {
            return lines
                .map( line -> line.trim() )
                .filter( line -> !line.isEmpty() && !line.startsWith("#") )
                .map( line -> {
                    int splitIndex = line.indexOf("=");
                    return new String[] { line.substring(0, splitIndex), line.substring(splitIndex+1) };
                })
                .collect( Collectors.toMap( 
                    tokens -> tokens[0], 
                    tokens -> tokens.length > 1 ? tokens[1] : "",
                    (val1, val2) -> val2
                ) );
        }
    }
    
}
