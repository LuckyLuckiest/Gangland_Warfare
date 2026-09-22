package org.luckyraven.gangland.data.gang;

import java.util.Optional;
import java.util.UUID;

/**
 * Core-owned "is/which gang" fact holder (R9, WS5 G1 step 9b) — the sibling of
 * {@link org.luckyraven.gangland.data.economy.BankTiers} for gang membership. Always present as a core bean
 * (bound zero-arg, exactly like {@code DataConfig.bankTiers()}) so {@code gadget}, {@code civilians} and
 * {@code cops-n-crooks} can read gang facts without ever declaring {@code Depends: [gang]}; inert (every method
 * returns its documented absent-default) until the gang module's {@code GangMembershipInstaller} calls
 * {@link #install(GangMembershipView)} from its {@code @PostConstruct}.
 *
 * <p>Replaces the earlier {@code MembershipLookupContract} sketch (S1): one holder instead of a narrower
 * per-module contract, so gadget/civilians/cops-n-crooks lose their {@code Depends: [gang]} edge entirely.
 */
public final class GangMembership {

	private volatile GangMembershipView view;

	public void install(GangMembershipView view) {
		this.view = view;
	}

	/**
	 * @return the gang id {@code uuid} currently belongs to, or {@code -1} when it belongs to none or no view is
	 * installed (module absent).
	 */
	public int gangIdOf(UUID uuid) {
		GangMembershipView current = this.view;
		return current == null || uuid == null ? -1 : current.gangIdOf(uuid);
	}

	/**
	 * @return whether the two gang ids are allied — strict, never same-gang (pinned so cops' turf friendly-fire
	 * keeps today's semantics). {@code false} when no view is installed.
	 */
	public boolean gangsAllied(int gangIdA, int gangIdB) {
		GangMembershipView current = this.view;
		return current != null && current.gangsAllied(gangIdA, gangIdB);
	}

	/**
	 * @return whether {@code a} and {@code b} belong to the same real gang, or to two allied gangs. {@code false}
	 * when either is gang-less or no view is installed.
	 */
	public boolean alliedOrSame(UUID a, UUID b) {
		int idA = gangIdOf(a);
		int idB = gangIdOf(b);

		return (idA == idB && idA != -1) || gangsAllied(idA, idB);
	}

	/**
	 * @return the gang's display name, or empty when {@code gangId} names no real gang or no view is installed
	 * (module absent) — mirrors {@link #gangIdOf(UUID)}'s absent-sentinel pattern (W54 F4).
	 */
	public Optional<String> nameOf(int gangId) {
		GangMembershipView current = this.view;
		return current == null ? Optional.empty() : current.nameOf(gangId);
	}
}
