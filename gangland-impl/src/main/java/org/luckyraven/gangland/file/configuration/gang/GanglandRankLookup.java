package org.luckyraven.gangland.file.configuration.gang;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.contract.RankLookupContract;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;

/**
 * Adapter exposing impl-side {@link RankManager} as a {@link RankLookupContract} so feature modules / repositories can
 * resolve ranks through the contract.
 */
public final class GanglandRankLookup implements RankLookupContract {

	private final RankManager delegate;

	public GanglandRankLookup(RankManager delegate) {
		this.delegate = delegate;
	}

	@Override
	public @Nullable Rank get(int rankId) {
		return delegate.get(rankId);
	}

	@Override
	public Rank getRootRank() {
		return delegate.getRankTree().getRoot().getData();
	}
}
