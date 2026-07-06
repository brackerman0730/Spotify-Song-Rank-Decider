package com.rankify.ui;

import com.rankify.model.Playlist;
import com.rankify.model.Song;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Drag-and-drop tier list builder.
 *
 * Layout:
 *   [ Unranked pool                              ]
 *   [ S | ...songs...                            ]
 *   [ A | ...songs...                            ]
 *   [ B | ...songs...                            ]
 *   ...
 *
 * Songs live inside FlowPanes so they wrap automatically. Drag any chip
 * from one row to another to move it. The label on the left of each row
 * can be clicked to rename that tier.
 */
public final class TierListView {

    // ------------------------------------------------------------------
    //  Default tiers (name, background color, text color).
    // ------------------------------------------------------------------
    private static final String[][] DEFAULT_TIERS = {
            {"S", "#ff7f7f"},
            {"A", "#ffbf7f"},
            {"B", "#ffdf7f"},
            {"C", "#ffff7f"},
            {"D", "#bfff7f"},
            {"F", "#7fbfff"},
    };

    private final Stage    stage;
    private final Playlist playlist;
    private final List<Song> songs;

    /** Ordered rows, top → bottom. Row 0 is always the "Unranked" pool. */
    private final List<TierRow> rows = new ArrayList<>();

    /** Songs by id for quick lookup. */
    private final Map<String, Song> songById = new HashMap<>();

    private VBox tierColumn;

    public TierListView(Stage stage, Playlist playlist, List<Song> songs) {
        this.stage    = stage;
        this.playlist = playlist;
        this.songs    = new ArrayList<>(songs);
        for (Song s : this.songs) songById.put(s.id(), s);
    }

    // ==================================================================
    //  Scene construction
    // ==================================================================
    public void show() {
        // ----- header row -----
        Label title = new Label("Tier List — " + playlist.name());
        title.getStyleClass().add("label-header");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button autoTierBtn = ghostButton("Auto-tier by rank");
        Button exportCsv   = primaryButton("Export as CSV");
        Button exportPng   = secondaryButton("Export as image");
        Button back        = ghostButton("Back");
        autoTierBtn.setOnAction(e -> autoTierByRank());
        exportCsv .setOnAction(e -> exportCsv());
        exportPng .setOnAction(e -> exportPng());
        back      .setOnAction(e -> new MainView(stage).show());

        HBox headerRow = new HBox(10, title, headerSpacer, autoTierBtn, exportCsv, exportPng, back);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // ----- build rows -----
        rows.clear();
        rows.add(new TierRow("Unranked", "#3a3a3a", true));  // pool
        for (String[] t : DEFAULT_TIERS) {
            rows.add(new TierRow(t[0], t[1], false));
        }

        // Every song starts in the pool.
        for (Song s : songs) rows.get(0).addSong(s);

        tierColumn = new VBox(8);
        tierColumn.setFillWidth(true);
        for (TierRow r : rows) tierColumn.getChildren().add(r.node);

        ScrollPane scroll = new ScrollPane(tierColumn);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:transparent; -fx-background:transparent;");

        VBox root = new VBox(18, headerRow, scroll);
        root.setPadding(new Insets(24));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Scene scene = new Scene(root, 1100, 720);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.setTitle("Rankify — Tier List");
    }

    // ==================================================================
    //  Tier row model + view
    // ==================================================================
    private final class TierRow {

        String name;
        String color;
        final boolean isPool;
        final FlowPane content;
        final Label   labelNode;
        final HBox    node;
        final List<Song> songs = new ArrayList<>();

        TierRow(String name, String color, boolean isPool) {
            this.name   = name;
            this.color  = color;
            this.isPool = isPool;

            content = new FlowPane();
            content.getStyleClass().add("tier-content");
            content.setPrefWrapLength(1);   // wrap when full
            HBox.setHgrow(content, Priority.ALWAYS);

            content.setOnDragOver   (e -> onDragOver(e, this));
            content.setOnDragEntered(e -> content.getStyleClass().add("tier-content-target"));
            content.setOnDragExited (e -> content.getStyleClass().remove("tier-content-target"));
            content.setOnDragDropped(e -> onDragDropped(e, this));

            if (isPool) {
                labelNode = new Label(name);
                labelNode.getStyleClass().add("label-section");
                labelNode.setMinWidth(70);
                labelNode.setAlignment(Pos.CENTER);
                content.getStyleClass().add("unranked-pool");
                node = new HBox(10, labelNode, content);
            } else {
                labelNode = new Label(name);
                labelNode.getStyleClass().add("tier-label");
                labelNode.setStyle("-fx-background-color:" + color + ";");
                labelNode.setOnMouseClicked(e -> promptRename());
                node = new HBox(10, labelNode, content);
                node.getStyleClass().add("tier-row");
            }
            node.setAlignment(Pos.CENTER_LEFT);
        }

        void addSong(Song s) {
            songs.add(s);
            content.getChildren().add(makeChip(s, this));
        }

        void removeSong(Song s) {
            songs.remove(s);
            content.getChildren().removeIf(n ->
                    s.id().equals(n.getProperties().get("songId")));
        }

        void promptRename() {
            TextInputDialog d = new TextInputDialog(name);
            d.setTitle("Rename tier");
            d.setHeaderText("New name for this tier:");
            d.setContentText("Name:");
            Theme.apply(d.getDialogPane().getScene());
            d.showAndWait().ifPresent(newName -> {
                String trimmed = newName.trim();
                if (!trimmed.isEmpty()) {
                    name = trimmed;
                    labelNode.setText(trimmed);
                }
            });
        }
    }

    // ==================================================================
    //  Song chip (draggable node)
    // ==================================================================
    private Label makeChip(Song s, TierRow home) {
        Label chip = new Label(s.title() + "  —  " + s.artist());
        chip.getStyleClass().add("song-chip");
        chip.getProperties().put("songId", s.id());

        chip.setOnDragDetected(e -> {
            Dragboard db = chip.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString(s.id());
            db.setContent(cc);
            chip.getStyleClass().add("song-chip-dragging");
            e.consume();
        });
        chip.setOnDragDone(e -> chip.getStyleClass().remove("song-chip-dragging"));
        return chip;
    }

    // ==================================================================
    //  Drag & drop handlers
    // ==================================================================
    private void onDragOver(DragEvent e, TierRow target) {
        if (e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.MOVE);
        e.consume();
    }

    private void onDragDropped(DragEvent e, TierRow target) {
        String id = e.getDragboard().getString();
        Song   s  = songById.get(id);
        boolean success = false;
        if (s != null) {
            // Remove from wherever it currently lives.
            for (TierRow r : rows) if (r.songs.contains(s)) r.removeSong(s);
            target.addSong(s);
            success = true;
        }
        e.setDropCompleted(success);
        e.consume();
    }

    // ==================================================================
    //  Actions
    // ==================================================================
    private void autoTierByRank() {
        // Distribute songs proportionally into the six named tiers based on
        // their order in the `songs` list (which reflects the finished ranking).
        List<Song> all = new ArrayList<>(songs);
        int total = all.size();
        if (total == 0) return;

        // Clear existing placements.
        for (TierRow r : rows) {
            r.songs.clear();
            r.content.getChildren().clear();
        }

        // 6 tiers, roughly proportional distribution. Adjust bucket sizes here.
        double[] pct = {0.10, 0.20, 0.25, 0.25, 0.15, 0.05};
        int idx = 0;
        for (int t = 0; t < 6 && idx < total; t++) {
            int count = (int) Math.round(pct[t] * total);
            if (t == 5) count = total - idx;                // last tier absorbs remainder
            for (int k = 0; k < count && idx < total; k++, idx++) {
                rows.get(t + 1).addSong(all.get(idx));      // +1 because row 0 is Unranked
            }
        }
    }

    private void exportCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export tier list");
        fc.setInitialFileName(playlist.name() + " (tiers).csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(file.getAbsolutePath())))) {
            out.println("Tier,Position,Song,Artist,Album,Spotify Track Id");
            for (TierRow r : rows) {
                for (int i = 0; i < r.songs.size(); i++) {
                    Song s = r.songs.get(i);
                    out.println(String.join(",",
                            csv(r.name),
                            String.valueOf(i + 1),
                            csv(s.title()),
                            csv(s.artist()),
                            csv(s.album()),
                            csv(s.id())));
                }
            }
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Exported to " + file.getName());
            Theme.apply(a.getDialogPane().getScene());
            a.showAndWait();
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Export failed: " + ex.getMessage());
            Theme.apply(a.getDialogPane().getScene());
            a.showAndWait();
        }
    }

    /** Snapshot the tier list VBox to a PNG file. */
    private void exportPng() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export tier list image");
        fc.setInitialFileName(playlist.name() + " (tiers).png");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        try {
            WritableImage img = tierColumn.snapshot(null, null);
            BufferedImage bimg = new BufferedImage(
                    (int) img.getWidth(), (int) img.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    bimg.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                }
            }
            ImageIO.write(bimg, "png", file);

            Alert a = new Alert(Alert.AlertType.INFORMATION, "Saved to " + file.getName());
            Theme.apply(a.getDialogPane().getScene());
            a.showAndWait();
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Image export failed: " + ex.getMessage());
            Theme.apply(a.getDialogPane().getScene());
            a.showAndWait();
        }
    }

    private String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n"))
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    // ==================================================================
    //  Button helpers
    // ==================================================================
    private Button primaryButton(String t)   { Button b = new Button(t); b.getStyleClass().add("button-primary");   return b; }
    private Button secondaryButton(String t) { Button b = new Button(t); b.getStyleClass().add("button-secondary"); return b; }
    private Button ghostButton(String t)     { Button b = new Button(t); b.getStyleClass().add("button-ghost");     return b; }
}