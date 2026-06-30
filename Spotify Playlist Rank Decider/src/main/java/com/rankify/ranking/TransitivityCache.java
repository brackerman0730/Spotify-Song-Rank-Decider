package com.rankify.ranking;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks the directed "is preferred over" graph that the user implicitly
 * builds as they answer comparisons. Used to skip questions whose answer
 * is already known by transitivity:
 *
 *     A &gt; B  and  B &gt; C   implies   A &gt; C
 *
 * Implementation:
 *   - Adjacency list keyed by song id.
 *   - On insert, we recompute reachable sets lazily via DFS.
 *
 * For playlists up to a few hundred songs this is trivially fast.
 */
public final class TransitivityCache {

    /** preferredOver.get(a) is the set of songs known to be less preferred than a. */
    private final Map<String, Set<String>> preferredOver = new HashMap<>();

    /** Record: {@code winner} is preferred over {@code loser}. */
    public void recordPreference(String winner, String loser) {
        preferredOver.computeIfAbsent(winner, k -> new HashSet<>()).add(loser);
    }

    /** True if {@code a} is known to be preferred over {@code b} (directly or by transitivity). */
    public boolean isPreferredOver(String a, String b) {
        return reachable(a, b, new HashSet<>());
    }

    private boolean reachable(String from, String target, Set<String> visited) {
        if (!visited.add(from)) return false;
        Set<String> direct = preferredOver.get(from);
        if (direct == null) return false;
        if (direct.contains(target)) return true;
        for (String next : direct) {
            if (reachable(next, target, visited)) return true;
        }
        return false;
    }

    public int size() { return preferredOver.size(); }
}