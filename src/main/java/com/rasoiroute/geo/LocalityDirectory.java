package com.rasoiroute.geo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A lookup table from locality name to its coordinates, loaded from simple CSV text:
 *
 *   # lines starting with # are comments
 *   name,latitude,longitude
 *   Navrangpura,23.0383,72.5600
 *
 * Names are matched ignoring upper/lower case and extra spaces.
 */
public final class LocalityDirectory {

    private final Map<String, Location> byKey;

    private LocalityDirectory(Map<String, Location> byKey) {
        this.byKey = byKey;
    }

    /** Reads the default list that ships inside the project (src/main/resources/localities.csv). */
    public static LocalityDirectory loadDefault() {
        try (InputStream in = LocalityDirectory.class.getResourceAsStream("/localities.csv")) {
            if (in == null) {
                throw new IllegalStateException("localities.csv was not found in the project resources");
            }
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line).append('\n');
                }
            }
            return fromCsv(text.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read localities.csv", e);
        }
    }

    public static LocalityDirectory fromCsv(String csv) {
        if (csv == null) {
            throw new IllegalArgumentException("CSV text is required");
        }
        Map<String, Location> map = new LinkedHashMap<>();
        String[] lines = csv.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.toLowerCase().startsWith("name,")) {
                continue;   // the header row
            }
            String[] parts = line.split(",");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Line " + (i + 1) + " must be: name,latitude,longitude");
            }
            double lat;
            double lon;
            try {
                lat = Double.parseDouble(parts[1].trim());
                lon = Double.parseDouble(parts[2].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Line " + (i + 1) + " has a latitude or longitude that is not a number");
            }
            Location location = new Location(parts[0], lat, lon);
            String key = key(location.name());
            if (map.containsKey(key)) {
                throw new IllegalArgumentException("Locality listed twice: " + location.name());
            }
            map.put(key, location);
        }
        return new LocalityDirectory(map);
    }

    /** Finds a locality, or explains clearly that it is unknown. */
    public Location find(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Locality name is required");
        }
        Location location = byKey.get(key(name));
        if (location == null) {
            throw new IllegalArgumentException("Unknown locality: " + name.trim());
        }
        return location;
    }

    public boolean contains(String name) {
        return name != null && byKey.containsKey(key(name));
    }

    public Set<String> names() {
        return byKey.keySet();
    }

    public int size() {
        return byKey.size();
    }

    private static String key(String name) {
        return name.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
