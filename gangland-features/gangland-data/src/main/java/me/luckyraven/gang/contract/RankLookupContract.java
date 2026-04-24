package me.luckyraven.gang.contract;

import me.luckyraven.gang.rank.Rank;
import org.jetbrains.annotations.Nullable;

/**
 * Read-side contract for looking up ranks — consumed by repositories and feature modules that need to resolve a
 * {@link Rank} without importing impl's {@code RankManager}.
 */
public interface RankLookupContract {

	@Nullable Rank get(int rankId);

	Rank getRootRank();
}
