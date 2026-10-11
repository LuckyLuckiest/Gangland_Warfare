package org.luckyraven.gangland.gang.purge;

/** A gang the purge would disband. */
public record PurgeCandidate(int gangId, String name, long lastSeenMillis) { }
