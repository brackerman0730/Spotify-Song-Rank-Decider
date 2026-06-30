package com.rankify.io;

import com.rankify.model.Playlist;

import java.io.IOException;

/**
 * Stub for the future Spotify Web API integration.
 *
 * When you obtain a Client ID / Secret, implement {@link #load(String)} to:
 *   1. Acquire a bearer token via the Client Credentials flow.
 *   2. GET https://api.spotify.com/v1/playlists/{id}/tracks (paginated).
 *   3. Map each track JSON object into {@link com.rankify.model.Song}.
 *
 * The rest of the application is already wired up against {@link PlaylistSource},
 * so swapping {@code CsvPlaylistSource} for this class is a one-line change
 * in {@link com.rankify.ui.MainView}.
 */
public final class SpotifyPlaylistSource implements PlaylistSource {

    private final String clientId;
    private final String clientSecret;

    public SpotifyPlaylistSource(String clientId, String clientSecret) {
        this.clientId     = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public Playlist load(String playlistUrlOrId) throws IOException {
        throw new UnsupportedOperationException(
            "Spotify API access is not yet wired up. " +
            "Use CsvPlaylistSource with a converter export for now."
        );
    }
}