package com.rankify.ui;

import com.rankify.io.CsvPlaylistSource;
import com.rankify.io.PlaylistSource;
import com.rankify.io.ProgressStore;
import com.rankify.model.Playlist;
import com.rankify.ranking.AdaptiveMergeSortRanker;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Paths;

/** Landing screen: pick a playlist file, optionally load a saved session. */
public final class MainView {

    private final Stage stage;

    public MainView(Stage stage) { this.stage = stage; }

    public void show() {
        Label title = new Label("Rankify");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");

        Label subtitle = new Label("Pairwise song ranker — powered by adaptive merge sort");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #777;");

        Button loadCsv     = new Button("Load playlist CSV...");
        Button resumeBtn   = new Button("Resume saved session...");
        Button spotifyBtn  = new Button("Load from Spotify URL  (coming soon)");
        spotifyBtn.setDisable(true);

        loadCsv.setOnAction(e -> chooseAndStart());
        resumeBtn.setOnAction(e -> chooseAndResume());

        VBox root = new VBox(15, title, subtitle, loadCsv, resumeBtn, spotifyBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        stage.setScene(new Scene(root, 520, 360));
        stage.setTitle("Rankify");
        stage.show();
    }

    private void chooseAndStart() {
        File f = csvChooser("Select playlist file").showOpenDialog(stage);
        if (f == null) return;
        try {
            PlaylistSource source = new CsvPlaylistSource();
            Playlist playlist = source.load(f.getAbsolutePath());
            if (playlist.size() < 2) {
                info("Playlist needs at least two songs to rank.");
                return;
            }
            AdaptiveMergeSortRanker ranker = new AdaptiveMergeSortRanker(playlist);
            new ComparisonView(stage, playlist, ranker).show();
        } catch (Exception ex) {
            error("Couldn't load playlist: " + ex.getMessage());
        }
    }

    private void chooseAndResume() {
        File playlistFile = csvChooser("Select the ORIGINAL playlist file").showOpenDialog(stage);
        if (playlistFile == null) return;
        File sessionFile = sessionChooser("Select saved session (.rkfy)").showOpenDialog(stage);
        if (sessionFile == null) return;

        try {
            Playlist playlist = new CsvPlaylistSource().load(playlistFile.getAbsolutePath());
            AdaptiveMergeSortRanker ranker = new AdaptiveMergeSortRanker(playlist);
            new ProgressStore().load(Paths.get(sessionFile.getAbsolutePath()), ranker);
            new ComparisonView(stage, playlist, ranker).show();
        } catch (Exception ex) {
            error("Couldn't resume: " + ex.getMessage());
        }
    }

    private FileChooser csvChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV / TXT", "*.csv", "*.txt"));
        return fc;
    }

    private FileChooser sessionChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Rankify session", "*.rkfy"));
        return fc;
    }

    private void info(String msg)  { new Alert(Alert.AlertType.INFORMATION, msg).showAndWait(); }
    private void error(String msg) { new Alert(Alert.AlertType.ERROR,       msg).showAndWait(); }
}