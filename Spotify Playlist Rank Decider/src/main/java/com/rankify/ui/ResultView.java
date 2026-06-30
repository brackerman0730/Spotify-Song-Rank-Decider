package com.rankify.ui;

import com.rankify.io.RankingCsvWriter;
import com.rankify.model.Playlist;
import com.rankify.model.Song;
import com.rankify.ranking.AdaptiveMergeSortRanker;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

/** Shows the finished ranking and offers to export it as CSV. */
public final class ResultView {

    private final Stage    stage;
    private final Playlist playlist;
    private final List<Song> ranking;
    private final AdaptiveMergeSortRanker ranker;

    public ResultView(Stage stage, Playlist playlist, List<Song> ranking,
                      AdaptiveMergeSortRanker ranker) {
        this.stage    = stage;
        this.playlist = playlist;
        this.ranking  = ranking;
        this.ranker   = ranker;
    }

    public void show() {
        Label header = new Label("Done — your ranking is ready 🎉");
        header.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label stats = new Label(String.format(
                "%d songs ranked using %d direct comparisons (%d more inferred).",
                ranking.size(),
                ranker.comparisonsAsked(),
                ranker.comparisonsSavedByInference()));

        // Build a simple table view
        TableView<Row> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(toRows(ranking)));

        table.getColumns().addAll(
                col("Rank",   "rank",   55),
                col("Song",   "title",  240),
                col("Artist", "artist", 180),
                col("Album",  "album",  220)
        );

        Button export = new Button("Export ranking as CSV...");
        export.setOnAction(e -> exportCsv());

        Button back = new Button("Back to start");
        back.setOnAction(e -> new MainView(stage).show());

        HBox actions = new HBox(15, export, back);
        actions.setAlignment(Pos.CENTER);

        VBox root = new VBox(15, header, stats, table, actions);
        root.setPadding(new Insets(25));
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        stage.setScene(new Scene(root, 820, 560));
        stage.setTitle("Rankify — Results");
    }

    private <T> TableColumn<Row, T> col(String name, String prop, double width) {
        TableColumn<Row, T> c = new TableColumn<>(name);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(width);
        return c;
    }

    private List<Row> toRows(List<Song> ranking) {
        List<Row> rows = new java.util.ArrayList<>();
        for (int i = 0; i < ranking.size(); i++) {
            Song s = ranking.get(i);
            rows.add(new Row(i + 1, s.title(), s.artist(), s.album()));
        }
        return rows;
    }

    private void exportCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save ranking CSV");
        fc.setInitialFileName(playlist.name() + " (ranked).csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        try {
            new RankingCsvWriter().write(Paths.get(file.getAbsolutePath()), ranking);
            new Alert(Alert.AlertType.INFORMATION, "Exported to " + file.getName()).showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Export failed: " + ex.getMessage()).showAndWait();
        }
    }

    /** JavaBean used purely so TableView's PropertyValueFactory works cleanly. */
    public static class Row {
        private final int rank;
        private final String title, artist, album;
        public Row(int rank, String title, String artist, String album) {
            this.rank = rank; this.title = title; this.artist = artist; this.album = album;
        }
        public int    getRank()   { return rank; }
        public String getTitle()  { return title; }
        public String getArtist() { return artist; }
        public String getAlbum()  { return album; }
    }
}