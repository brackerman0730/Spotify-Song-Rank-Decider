package com.rankify.io;

import com.rankify.model.Playlist;
import com.rankify.model.Song;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the "key: value" style export produced by tools like TuneMyMusic.
 * Each track is delimited by a line beginning with "#:".
 *
 * Designed to be lenient: any unknown field is ignored, and any missing
 * field falls back to a sensible default.
 */
public final class CsvPlaylistSource implements PlaylistSource {

    @Override
    public Playlist load(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        List<Song> songs = new ArrayList<>();
        Song.Builder current = null;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) continue;

            // Each new track begins with "#: N"
            if (line.startsWith("#:")) {
                if (current != null) songs.add(safeBuild(current));
                current = Song.builder();
                continue;
            }
            if (current == null) continue;

            int colon = line.indexOf(':');
            if (colon < 0) continue;

            String key   = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            applyField(current, key, value);
        }
        if (current != null) songs.add(safeBuild(current));

        String name = path.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        return new Playlist(name, songs);
    }

    /** Map a single "Key: value" pair onto the builder. */
    private void applyField(Song.Builder b, String key, String value) {
        switch (key) {
            case "Song"             -> b.title(value);
            case "Artist"           -> b.artist(value);
            case "Album"            -> b.album(value);
            case "Album Date"       -> b.albumDate(value);
            case "BPM"              -> b.bpm(parseInt(value));
            case "Popularity"       -> b.popularity(parseInt(value));
            case "Energy"           -> b.energy(parseInt(value));
            case "Key"              -> b.key(value);
            case "Camelot"          -> b.camelot(value);
            case "Genres"           -> b.genres(value);
            case "Spotify Track Id" -> b.id(value);
            case "Explicit"         -> b.explicit(value.equalsIgnoreCase("yes"));
            case "Duration"         -> b.durationSeconds(parseDuration(value));
            default                 -> { /* ignore unknown */ }
        }
    }

    /** If a track lacks an ID (e.g. user-edited file) we still want it loaded. */
    private Song safeBuild(Song.Builder b) {
        try { return b.build(); }
        catch (NullPointerException ex) {
            // Synthesise placeholders for any missing required field.
            return b.id("anon-" + System.nanoTime())
                    .title("Unknown")
                    .artist("Unknown")
                    .build();
        }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.strip()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static int parseDuration(String s) {
        // "03:28" -> 208
        String[] parts = s.split(":");
        if (parts.length != 2) return 0;
        return parseInt(parts[0]) * 60 + parseInt(parts[1]);
    }
}