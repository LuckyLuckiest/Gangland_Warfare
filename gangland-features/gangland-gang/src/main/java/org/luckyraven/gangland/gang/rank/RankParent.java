package org.luckyraven.gangland.gang.rank;

/**
 * One link of the rank tree: {@code rankId} sits directly above {@code childId}. The table columns stay {@code id}
 * (the parent) and {@code parent_id} (the CHILD), a historical name kept so existing databases keep working.
 */
public record RankParent(int rankId, int childId) { }