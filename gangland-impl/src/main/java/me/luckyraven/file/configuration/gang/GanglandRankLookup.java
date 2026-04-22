package me.luckyraven.file.configuration.gang;

import me.luckyraven.gang.contract.RankLookupContract;
import me.luckyraven.gang.rank.Rank;
import me.luckyraven.gang.rank.RankManager;
import org.jetbrains.annotations.Nullable;

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
