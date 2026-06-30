package com.rankify.ui;

import com.rankify.io.ProgressStore;
import com.rankify.model.Playlist;
import com.rankify.model.Song;
import com.rankify.ranking.AdaptiveMergeSortRanker;
import com.rankify.ranking.ComparisonChoice;
import com.rankify.ranking.ComparisonRequest;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Paths;
import java.util.Optional;

/** The main comparison screen: two cards, four choices, a save button. */
public final class ComparisonView {

    private final Stage  stage;
    private final Playlist playlist;
    private final AdaptiveMergeSortRanker ranker;

    private final Label       leftCard   = new Label();
    private final Label       rightCard  = new Label();
    private final Label       leftMeta   = new Label();
    private final Label       rightMeta  = new Label();
    private final Label       header     = new Label();
    private final Label       stats      = new Label();
    private final ProgressBar progress   = new ProgressBar(0);

    public ComparisonView(Stage stage, Playlist playlist, AdaptiveMergeSortRanker ranker) {
        this.stage    = stage;
        this.playlist = playlist;
        this.ranker   = ranker;
    }

    public void show() {
        // ----- header -----
        header.setText("Which song do you prefer?");
        header.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        // ----- two cards -----
        VBox left  = buildCard(leftCard,  leftMeta);
        VBox right = buildCard(rightCard, rightMeta);
        HBox cards = new HBox(20, left, right);
        cards.setAlignment(Pos.CENTER);
        HBox.setHgrow(left,  Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        // ----- choice buttons -----
        Button pickLeft  = new Button("◀ Pick Left");
        Button pickRight = new Button("Pick Right ▶");
        Button unknown   = new Button("I don't know this song");
        Button tie       = new Button("Skip (can't decide)");

        pickLeft.setOnAction (e -> answer(ComparisonChoice.LEFT));
        pickRight.setOnAction(e -> answer(ComparisonChoice.RIGHT));
        unknown.setOnAction  (e -> answer(ComparisonChoice.SKIP_UNKNOWN));
        tie.setOnAction      (e -> answer(ComparisonChoice.SKIP_TIE));

        for (Button b : new Button[]{pickLeft, pickRight, unknown, tie}) {
            b.setPrefHeight(40);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }

        HBox primary   = new HBox(15, pickLeft, pickRight);
        HBox secondary = new HBox(15, unknown, tie);

        // ----- progress + save -----
        Button save = new Button("💾 Save & Exit");
        save.setOnAction(e -> saveAndExit());

        HBox bottom = new HBox(15, progress, save);
        bottom.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progress, Priority.ALWAYS);
        progress.setMaxWidth(Double.MAX_VALUE);

        // ----- assemble -----
        VBox root = new VBox(20, header, cards, primary, secondary, stats, bottom);
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.TOP_CENTER);

        stage.setScene(new Scene(root, 820, 560));
        stage.setTitle("Rankify — " + playlist.name());

        refresh();
    }

    private VBox buildCard(Label title, Label meta) {
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #222;");
        title.setWrapText(true);
        meta.setStyle("-fx-font-size: 13px; -fx-text-fill: #555;");
        meta.setWrapText(true);

        VBox card = new VBox(10, title, meta);
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("""
                -fx-background-color: #fafafa;
                -fx-border-color: #ddd;
                -fx-border-radius: 10;
                -fx-background-radius: 10;
                """);
        card.setMinHeight(180);
        return card;
    }

    private void answer(ComparisonChoice choice) {
        if (ranker.nextRequest().isEmpty()) return;
        ranker.submit(choice);
        refresh();
    }

    private void refresh() {
        Optional<ComparisonRequest> next = ranker.nextRequest();

        if (next.isEmpty()) {
            new ResultView(stage, playlist, ranker.finalRanking(), ranker).show();
            return;
        }

        ComparisonRequest req = next.get();
        leftCard .setText(req.left().title()  + "\n" + req.left().artist());
        rightCard.setText(req.right().title() + "\n" + req.right().artist());
        leftMeta .setText(buildMeta(req.left()));
        rightMeta.setText(buildMeta(req.right()));

        int asked   = ranker.comparisonsAsked();
        int saved   = ranker.comparisonsSavedByInference();
        int estTot  = ranker.estimatedTotalComparisons();
        progress.setProgress(Math.min(1.0, asked / (double) estTot));
        stats.setText(String.format(
                "Comparison %d   •   %d auto-resolved by transitivity   •   ~%d max",
                asked, saved, estTot));
    }

    private String buildMeta(Song s) {
        StringBuilder sb = new StringBuilder();
        sb.append("Album: ").append(s.album().isEmpty() ? "—" : s.album()).append('\n');
        sb.append("Duration: ").append(s.formattedDuration()).append("   ");
        if (s.bpm() > 0)        sb.append("BPM: ").append(s.bpm()).append("   ");
        if (!s.key().isEmpty()) sb.append("Key: ").append(s.key()).append("   ");
        if (s.popularity() > 0) sb.append("\nPopularity: ").append(s.popularity()).append("/100");
        if (s.explicit())       sb.append("   🅴");
        return sb.toString();
    }

    private void saveAndExit() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save session");
        fc.setInitialFileName(playlist.name() + ".rkfy");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Rankify session", "*.rkfy"));
        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        try {
            new ProgressStore().save(Paths.get(file.getAbsolutePath()), ranker);
            new Alert(Alert.AlertType.INFORMATION,
                    "Session saved. Re-open it from the main screen later.").showAndWait();
            new MainView(stage).show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Save failed: " + ex.getMessage()).showAndWait();
        }
    }
}