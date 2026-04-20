package me.luckyraven.copsncrooks.npc.banker.tier;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class BankTierRegistry {

	private final AtomicReference<List<BankTier>> ordered = new AtomicReference<>(Collections.emptyList());

	@Nullable
	public BankTier get(String id) {
		if (id == null) return null;
		for (BankTier tier : ordered.get()) {
			if (tier.id().equalsIgnoreCase(id)) return tier;
		}
		return null;
	}

	public boolean exists(String id) {
		return get(id) != null;
	}

	public List<BankTier> all() {
		return ordered.get();
	}

	public Set<String> ids() {
		Set<String> keys = new LinkedHashSet<>();
		for (BankTier tier : ordered.get()) keys.add(tier.id());
		return keys;
	}

	/**
	 * Returns the next tier in the ladder (strictly greater {@code order}) or {@code null} if the caller is already at
	 * the top tier. Used by the upgrade flow.
	 */
	@Nullable
	public BankTier next(@Nullable String currentId) {
		List<BankTier> list = ordered.get();
		if (list.isEmpty()) return null;

		if (currentId == null) return list.get(0);

		BankTier current  = get(currentId);
		int      baseline = current == null ? Integer.MIN_VALUE : current.order();

		BankTier best = null;
		for (BankTier tier : list) {
			if (tier.order() <= baseline) continue;
			if (best == null || tier.order() < best.order()) best = tier;
		}
		return best;
	}

	@Nullable
	public BankTier first() {
		List<BankTier> list = ordered.get();
		return list.isEmpty() ? null : list.get(0);
	}

	public void replaceAll(Map<String, BankTier> replacement) {
		List<BankTier> sorted = new ArrayList<>(replacement.values());
		sorted.sort(Comparator.comparingInt(BankTier::order));
		ordered.set(Collections.unmodifiableList(sorted));
	}

}
