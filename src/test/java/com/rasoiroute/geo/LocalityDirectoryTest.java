package com.rasoiroute.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalityDirectoryTest {

    private static final String CSV = """
            # a comment line
            name,latitude,longitude

            Navrangpura,23.0383,72.5600
            Prahlad Nagar,23.0100,72.5100
            """;

    @Test
    void readsLocalitiesAndSkipsCommentsHeaderAndBlankLines() {
        LocalityDirectory dir = LocalityDirectory.fromCsv(CSV);
        assertEquals(2, dir.size());
        assertEquals(23.0383, dir.find("Navrangpura").latitude(), 1e-9);
    }

    @Test
    void lookupIgnoresCaseAndExtraSpaces() {
        LocalityDirectory dir = LocalityDirectory.fromCsv(CSV);
        assertEquals("Prahlad Nagar", dir.find("  prahlad    NAGAR ").name());
        assertTrue(dir.contains("NAVRANGPURA"));
        assertFalse(dir.contains("Bopal"));
    }

    @Test
    void unknownLocalityGivesAClearError() {
        LocalityDirectory dir = LocalityDirectory.fromCsv(CSV);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> dir.find("Atlantis"));
        assertTrue(e.getMessage().contains("Atlantis"));
    }

    @Test
    void duplicateLocalityIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> LocalityDirectory.fromCsv("A,23.0,72.0\na,23.1,72.1"));
    }

    @Test
    void malformedLinesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LocalityDirectory.fromCsv("OnlyName"));
        assertThrows(IllegalArgumentException.class, () -> LocalityDirectory.fromCsv("A,abc,72.0"));
        assertThrows(IllegalArgumentException.class, () -> LocalityDirectory.fromCsv("A,95.0,72.0"));
    }

    @Test
    void edgeCase_emptyTextGivesAnEmptyDirectory() {
        assertEquals(0, LocalityDirectory.fromCsv("").size());
        assertThrows(IllegalArgumentException.class, () -> LocalityDirectory.fromCsv(null));
    }

    @Test
    void blankLookupIsRejected() {
        LocalityDirectory dir = LocalityDirectory.fromCsv(CSV);
        assertThrows(IllegalArgumentException.class, () -> dir.find(" "));
        assertThrows(IllegalArgumentException.class, () -> dir.find(null));
    }

    @Test
    void defaultListShipsWithTheProjectAndHasTheKitchen() {
        LocalityDirectory dir = LocalityDirectory.loadDefault();
        assertTrue(dir.size() >= 10);
        assertTrue(dir.contains("Kitchen"));
    }
}
