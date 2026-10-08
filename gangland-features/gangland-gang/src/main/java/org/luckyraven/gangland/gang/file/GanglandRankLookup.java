package org.luckyraven.gangland.gang.file;

import lombok.CustomLog;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.contract.RankLookupContract;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;

/**
 * Adapter exposing impl-side {@link RankManager} as a {@link RankLookupContract} so feature modules / repositories can
 * resolve ranks through the contract.
 */
@CustomLog
public final class GanglandRankLookup implements RankLookupContract {

	private final RankManager delegate;

	public GanglandRankLookup(RankManager delegate) {
		this.delegate = delegate;
	}

	@Override
	public @Nullable Rank get(int rankId) {
		return delegate.get(rankId);
	}

	/**
	 * GR-28: an empty rank tree has no root; fall back to the tail rank (may still be null).
	 */
	@Override
	public @Nullable Rank getRootRank() {
		if (delegate.getRankTree().getRoot() == null) {
			log.warn("The rank tree has no root; falling back to the tail rank");
			return delegate.get(GangSettings.getGangRankTail());
		}
		return delegate.getRankTree().getRoot().getData();
	}
}
