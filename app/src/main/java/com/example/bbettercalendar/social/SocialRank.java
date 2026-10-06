package com.example.bbettercalendar.social;

/**
 * The user's position among friends for today's focus time (html 02 "Best · 2nd among friends").
 * {@code position} is 1-based; {@code groupSize} counts the user plus every friend.
 */
public final class SocialRank {

    private final int position;
    private final int groupSize;

    public SocialRank(int position, int groupSize) {
        if (position < 1 || groupSize < position) {
            throw new IllegalArgumentException("rank " + position + " of " + groupSize);
        }
        this.position = position;
        this.groupSize = groupSize;
    }

    public int getPosition() { return position; }

    public int getGroupSize() { return groupSize; }

    public boolean isFirst() { return position == 1; }
}
