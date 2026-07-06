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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

    private final Label leftTitle   = new Label();
    private final Label leftArtist  = new Label();
    private final Label leftMeta    = new Label();
    private final Label rightTitle  = new Label();
    private final Label rightArtist = new Label();
    private final Label rightMeta   = new Label();
    private final Label header      = new Label();
    private final Label stats       = new Label();
    private final ProgressBar progress = new ProgressBar(0);

    private VBox leftCard;
    private VBox rightCard;

    public ComparisonView(Stage stage, Playlist playlist, AdaptiveMergeSortRanker ranker) {
        this.stage    = stage;
        this.playlist = playlist;
        this.ranker   = ranker;
    }

    public void show() {
        // ----- header -----
        header.setText("Which do you prefer?");
        header.getStyleClass().add("label-header");

        // ----- two cards -----
        leftCard  = buildCard(leftTitle,  leftArtist,  leftMeta);
        rightCard = buildCard(rightTitle, rightArtist, rightMeta);

        // Click the card itself to pick it (in addition to the buttons below)
        leftCard.setOnMouseClicked (e -> answer(ComparisonChoice.LEFT));
        rightCard.setOnMouseClicked(e -> answer(ComparisonChoice.RIGHT));

        HBox cards = new HBox(20, leftCard, rightCard);
        cards.setAlignment(Pos.CENTER);
        HBox.setHgrow(leftCard,  Priority.ALWAYS);
        HBox.setHgrow(rightCard, Priority.ALWAYS);

        // ----- choice buttons -----
        Button pickLeft  = primaryButton("◀ Pick Left");
        Button pickRight = primaryButton("Pick Right ▶");
        Button unknown   = secondaryButton("I don't know one of these");
        Button tie       = secondaryButton("Skip (can't decide)");

        pickLeft.setOnAction (e -> answer(ComparisonChoice.LEFT));
        pickRight.setOnAction(e -> answer(ComparisonChoice.RIGHT));
        unknown.setOnAction  (e -> askWhichUnknown());
        tie.setOnAction      (e -> answer(ComparisonChoice.SKIP_TIE));

        pickLeft.setTooltip (new Tooltip("Left song is preferred"));
        pickRight.setTooltip(new Tooltip("Right song is preferred"));
        unknown.setTooltip  (new Tooltip("Remove unknown song(s) from the final ranking"));
        tie.setTooltip      (new Tooltip("Auto-resolved by popularity (not cached)"));

        for (Button b : new Button[]{pickLeft, pickRight, unknown, tie}) {
            b.setPrefHeight(44);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }

        HBox primary   = new HBox(15, pickLeft, pickRight);
        HBox secondary = new HBox(15, unknown, tie);

        // ----- progress + save -----
        stats.getStyleClass().add("label-stats");

        Button save = ghostButton("Save & Exit");
        save.setOnAction(e -> saveAndExit());

        HBox bottom = new HBox(15, progress, save);
        bottom.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progress, Priority.ALWAYS);
        progress.setMaxWidth(Double.MAX_VALUE);

        // ----- assemble -----
        Region spacer = new Region();
        spacer.setPrefHeight(6);

        VBox root = new VBox(18, header, cards, primary, secondary, spacer, stats, bottom);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_CENTER);

        Scene scene = new Scene(root, 900, 620);
        Theme.apply(scene);

        stage.setScene(scene);
        stage.setTitle("Rankify — " + playlist.name());

        refresh();
    }

    // ------------------------------------------------------------------

    private VBox buildCard(Label title, Label artist, Label meta) {
        title.getStyleClass().add("label-song-title");
        title.setWrapText(true);
        artist.getStyleClass().add("label-song-artist");
        artist.setWrapText(true);
        meta.getStyleClass().add("label-song-meta");
        meta.setWrapText(true);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox card = new VBox(6, title, artist, spacer, meta);
        card.getStyleClass().add("song-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setMinHeight(200);
        return card;
    }

    private Button primaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("button-primary");
        return b;
    }
    private Button secondaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("button-secondary");
        return b;
    }
    private Button ghostButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("button-ghost");
        return b;
    }

    // ------------------------------------------------------------------

    private void answer(ComparisonChoice choice) {
        if (ranker.nextRequest().isEmpty()) return;
        ranker.submit(choice);
        refresh();
    }

    private void askWhichUnknown() {
        if (ranker.nextRequest().isEmpty()) return;
        ComparisonRequest req = ranker.nextRequest().get();

        ButtonType leftBtn  = new ButtonType("Left: " + req.left().title());
        ButtonType rightBtn = new ButtonType("Right: " + req.right().title());
        ButtonType bothBtn  = new ButtonType("Both");
        ButtonType cancel   = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Remove unknown song");
        dialog.setHeaderText("Which song don't you know?");
        dialog.setContentText("Unknown songs will be removed from your final ranking.");
        dialog.getButtonTypes().setAll(leftBtn, rightBtn, bothBtn, cancel);
        Theme.apply(dialog.getDialogPane().getScene());

        dialog.showAndWait().ifPresent(result -> {
            if      (result == leftBtn)  answer(ComparisonChoice.REMOVE_LEFT);
            else if (result == rightBtn) answer(ComparisonChoice.REMOVE_RIGHT);
            else if (result == bothBtn)  answer(ComparisonChoice.REMOVE_BOTH);
        });
    }

    private void refresh() {
        Optional<ComparisonRequest> next = ranker.nextRequest();
        if (next.isEmpty()) {
            new ResultView(stage, playlist, ranker.finalRanking(), ranker).show();
            return;
        }

        ComparisonRequest req = next.get();
        leftTitle .setText(req.left().title());
        leftArtist.setText(req.left().artist());
        leftMeta  .setText(buildMeta(req.left()));

        rightTitle .setText(req.right().title());
        rightArtist.setText(req.right().artist());
        rightMeta  .setText(buildMeta(req.right()));

        int asked   = ranker.comparisonsAsked();
        int saved   = ranker.comparisonsSavedByInference();
        int estTot  = ranker.estimatedTotalComparisons();
        progress.setProgress(Math.min(1.0, asked / (double) estTot));
        stats.setText(String.format(
                "Comparison %d   •   %d auto-resolved   •   ~%d max",
                asked, saved, estTot));
    }

    private String buildMeta(Song s) {
        StringBuilder sb = new StringBuilder();
        if (!s.album().isEmpty()) sb.append(s.album()).append('\n');
        if (s.durationSeconds() > 0) sb.append(s.formattedDuration()).append("   ");
        if (s.bpm() > 0)             sb.append(s.bpm()).append(" BPM   ");
        if (!s.key().isEmpty())      sb.append(s.key()).append("   ");
        if (s.popularity() > 0)      sb.append("\nPopularity: ").append(s.popularity()).append("/100");
        if (s.explicit())            sb.append("   🅴");
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
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "Session saved. Re-open it from the main screen later.");
            Theme.apply(a.getDialogPane().getScene());
            a.showAndWait();
            new MainView(stage).show();
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Save failed: " + ex.getMessage());
            Theme.apply(a.getDialogPane().getScene());
            a.showAndWait();
        }
    }
}