package com.rankify.ranking;

import com.rankify.model.Playlist;
import com.rankify.model.Song;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bottom-up, adaptive merge sort driven by user comparisons.
 *
 *  - Bottom-up so the entire state is a handful of integers + arrays,
 *    which makes save/resume trivial (no recursion stack to serialise).
 *  - "Adaptive" because:
 *       1. Comparisons whose answer is implied by transitivity are skipped.
 *       2. When one side of a merge runs out, the rest of the other side
 *          is appended without further comparison (standard merge sort win).
 *
 * The algorithm produces a *descending* ranking: index 0 is the user's
 * most-preferred song.
 *
 * Usage:
 *   var ranker = new AdaptiveMergeSortRanker(playlist);
 *   while (ranker.nextRequest().isPresent()) {
 *       ComparisonRequest req = ranker.nextRequest().get();
 *       // show req.left() and req.right() to the user...
 *       ranker.submit(userChoice);
 *   }
 *   List<Song> ranked = ranker.finalRanking();
 */
public final class AdaptiveMergeSortRanker {

    /** Songs in their original order, indexed by integer. */
    private final List<Song> songs;
    private final Map<String, Integer> idToIndex = new HashMap<>();

    /** Current working permutation of indices. */
    private int[] order;

    /** Bottom-up merge sort cursors. */
    private int width;          // size of each run being merged
    private int leftStart;      // index where current merge's left run starts
    private int mid;            // boundary between left and right
    private int rightEnd;       // exclusive end of right run
    private int i, j;           // pointers within left / right
    private int[] buffer;       // accumulating merged output
    private int   bufferSize;

    /** True once everything has been merged into a single sorted run. */
    private boolean done;

    /** Comparisons performed and inferred — used for stats + skipping. */
    private int comparisonsAsked;
    private int comparisonsSkippedByCache;

    /** Cached preferences from prior user answers. */
    private final TransitivityCache cache = new TransitivityCache();

    /** The pending question awaiting an answer from the UI, if any. */
    private ComparisonRequest pending;

    // ------------------------------------------------------------------

    public AdaptiveMergeSortRanker(Playlist playlist) {
        this.songs = new ArrayList<>(playlist.songs());
        this.order = new int[songs.size()];
        for (int k = 0; k < songs.size(); k++) {
            order[k] = k;
            idToIndex.put(songs.get(k).id(), k);
        }
        this.width     = 1;
        this.leftStart = 0;
        this.done      = songs.size() <= 1;
        advanceUntilQuestion();
    }

    // ----- public API -----

    public Optional<ComparisonRequest> nextRequest() {
        if (done) return Optional.empty();
        return Optional.ofNullable(pending);
    }

    /** Apply the user's answer to the pending question and advance. */
    public void submit(ComparisonChoice choice) {
        if (pending == null) throw new IllegalStateException("No pending comparison.");
        Song left  = pending.left();
        Song right = pending.right();
        pending    = null;

        boolean leftWins = resolve(choice, left, right);

        // Cache only firm decisions, never skips.
        if (choice == ComparisonChoice.LEFT)  cache.recordPreference(left.id(),  right.id());
        if (choice == ComparisonChoice.RIGHT) cache.recordPreference(right.id(), left.id());

        applyMergeStep(leftWins);
        advanceUntilQuestion();
    }

    public boolean isFinished() { return done; }

    public int comparisonsAsked()           { return comparisonsAsked; }
    public int comparisonsSavedByInference(){ return comparisonsSkippedByCache; }

    /** A live ranking that becomes the true answer once finished. */
    public List<Song> finalRanking() {
        List<Song> result = new ArrayList<>(order.length);
        for (int idx : order) result.add(songs.get(idx));
        return result;
    }

    /** Rough upper bound on total comparisons (worst-case N * ceil(log2 N)). */
    public int estimatedTotalComparisons() {
        int n = songs.size();
        if (n <= 1) return 0;
        int logN = 32 - Integer.numberOfLeadingZeros(n - 1);
        return n * logN;
    }

    // ----- internal merge-sort state machine -----

    /**
     * Drive the merge forward until either:
     *   - we need to ask the user a question (sets {@code pending}), or
     *   - the entire sort is finished (sets {@code done}).
     */
    private void advanceUntilQuestion() {
        while (!done && pending == null) {

            // Start a new merge if there isn't one in progress.
            if (buffer == null) {
                if (leftStart >= songs.size() - width) {
                    // No more merges at this width → move to the next pass.
                    width *= 2;
                    leftStart = 0;
                    if (width >= songs.size()) { done = true; return; }
                    continue;
                }
                mid       = Math.min(leftStart + width, songs.size());
                rightEnd  = Math.min(leftStart + 2 * width, songs.size());
                i         = leftStart;
                j         = mid;
                buffer    = new int[rightEnd - leftStart];
                bufferSize = 0;
            }

            // If one side is exhausted, drain the other and finalise.
            if (i >= mid)      { drainFromRight(); continue; }
            if (j >= rightEnd) { drainFromLeft();  continue; }

            // Can we answer this comparison from the cache?
            Song leftSong  = songs.get(order[i]);
            Song rightSong = songs.get(order[j]);
            if (cache.isPreferredOver(leftSong.id(),  rightSong.id())) {
                comparisonsSkippedByCache++;
                applyMergeStep(true);
                continue;
            }
            if (cache.isPreferredOver(rightSong.id(), leftSong.id())) {
                comparisonsSkippedByCache++;
                applyMergeStep(false);
                continue;
            }

            // Otherwise we need the user's input — surface a question.
            pending = new ComparisonRequest(leftSong, rightSong);
            comparisonsAsked++;
            return;
        }
    }

    /** Advance one element of the active merge. */
    private void applyMergeStep(boolean leftWins) {
        if (leftWins) {
            buffer[bufferSize++] = order[i++];
        } else {
            buffer[bufferSize++] = order[j++];
        }
        if (i >= mid && j >= rightEnd) finishMerge();
    }

    private void drainFromLeft()  { while (i < mid)      buffer[bufferSize++] = order[i++]; finishMerge(); }
    private void drainFromRight() { while (j < rightEnd) buffer[bufferSize++] = order[j++]; finishMerge(); }

    private void finishMerge() {
        System.arraycopy(buffer, 0, order, leftStart, buffer.length);
        buffer    = null;
        leftStart += 2 * width;
    }

    /** Turn a {@link ComparisonChoice} into "did the left side win?". */
    private boolean resolve(ComparisonChoice choice, Song left, Song right) {
        return switch (choice) {
            case LEFT  -> true;
            case RIGHT -> false;
            case SKIP_UNKNOWN, SKIP_TIE -> left.popularity() >= right.popularity();
        };
    }

    // ----- save / resume hooks (used by ProgressStore) -----

    public Snapshot snapshot() {
        return new Snapshot(
            songIds(order),
            width, leftStart, mid, rightEnd, i, j,
            buffer == null ? null : songIds(buffer),
            bufferSize, done, comparisonsAsked, comparisonsSkippedByCache,
            cache
        );
    }

    public void restore(Snapshot s) {
        this.order      = indicesOf(s.orderIds);
        this.width      = s.width;
        this.leftStart  = s.leftStart;
        this.mid        = s.mid;
        this.rightEnd   = s.rightEnd;
        this.i          = s.i;
        this.j          = s.j;
        this.buffer     = s.bufferIds == null ? null : indicesOf(s.bufferIds);
        this.bufferSize = s.bufferSize;
        this.done       = s.done;
        this.comparisonsAsked          = s.comparisonsAsked;
        this.comparisonsSkippedByCache = s.comparisonsSkipped;
        // (cache is already wired in by reference)
        this.pending = null;
        advanceUntilQuestion();
    }

    private String[] songIds(int[] indices) {
        String[] ids = new String[indices.length];
        for (int k = 0; k < indices.length; k++) ids[k] = songs.get(indices[k]).id();
        return ids;
    }

    private int[] indicesOf(String[] ids) {
        int[] out = new int[ids.length];
        for (int k = 0; k < ids.length; k++) out[k] = idToIndex.getOrDefault(ids[k], 0);
        return out;
    }

    public TransitivityCache cache() { return cache; }
    public List<Song> songs() { return songs; }

    /** Plain-data carrier of all mutable algorithm state. */
    public record Snapshot(
            String[] orderIds,
            int width, int leftStart, int mid, int rightEnd,
            int i, int j,
            String[] bufferIds, int bufferSize,
            boolean done,
            int comparisonsAsked, int comparisonsSkipped,
            TransitivityCache cache
    ) {
        @Override public String toString() {
            return "Snapshot(width=" + width + ", leftStart=" + leftStart +
                   ", asked=" + comparisonsAsked + ", saved=" + comparisonsSkipped + ")";
        }
    }
}