package com.rankify.ranking;

/**
 * The four possible answers a user can give when shown two songs.
 *
 *  LEFT          — left song is preferred.
 *  RIGHT         — right song is preferred.
 *  SKIP_UNKNOWN  — user is unfamiliar with one/both; auto-resolve by popularity.
 *  SKIP_TIE      — user genuinely can't choose; auto-resolve by popularity.
 *
 * Skip choices are NOT added to the transitivity cache so the algorithm
 * doesn't propagate weak preferences as if they were strong ones.
 */
public enum ComparisonChoice {
    LEFT, RIGHT, SKIP_UNKNOWN, SKIP_TIE
}