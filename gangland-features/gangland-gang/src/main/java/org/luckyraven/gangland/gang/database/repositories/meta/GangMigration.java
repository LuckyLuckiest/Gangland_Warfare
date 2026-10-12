package org.luckyraven.gangland.gang.database.repositories.meta;

/**
 * One row of {@code gang_migration}: a one-shot data migration that ran, and when (W2-K).
 *
 * @param id     the migration id
 * @param doneAt epoch ms the migration was recorded
 */
public record GangMigration(String id, long doneAt) {
}
