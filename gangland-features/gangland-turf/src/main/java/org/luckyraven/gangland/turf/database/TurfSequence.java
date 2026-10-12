package org.luckyraven.gangland.turf.database;

/**
 * One row of {@code turf_sequence}. The table only ever holds the row with {@code id = 1}.
 *
 * @param id        the row key, always {@code 1}
 * @param highestId the highest turf id ever allocated
 */
public record TurfSequence(int id, int highestId) {
}
