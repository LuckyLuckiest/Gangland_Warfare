package org.luckyraven.gangland.data.gang;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
	 * @return whether the gang module has installed a live view. Lets a caller distinguish "the module isn't
	 * installed, so this holder can't answer either way" from "the module answered no" — {@link #nameOf(int)}
	 * alone can't tell those apart, since both return an absent result (W54 second re-review: restores
	 * {@code WaypointGangIdCommand}'s old {@code gang == null -> GANG_DOESNT_EXIST} guard, which only fires when
	 * there's an actual answer to distrust).
	 */
	public boolean isInstalled() {
		return this.view != null;
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

	/**
	 * @return whether {@code attacker} may not hurt {@code victim} at {@code at}. Without a view, the pre-2.6
	 * {@link #alliedOrSame(UUID, UUID)} rule (always {@code false} then).
	 *
	 * @since gangland-api 2.6
	 */
	public boolean pvpBlocked(UUID attacker, UUID victim, @Nullable Location at) {
		GangMembershipView current = this.view;
		if (current == null || attacker == null || victim == null) return false;

		return current.pvpBlocked(attacker, victim, at);
	}

	/**
	 * @return every real gang id; empty without a view.
	 *
	 * @since gangland-api 2.6
	 */
	public Set<Integer> gangIds() {
		GangMembershipView current = this.view;
		return current == null ? Collections.emptySet() : current.gangIds();
	}

	/**
	 * @return the id of the gang with this name, or {@code -1} (also without a view).
	 *
	 * @since gangland-api 2.6
	 */
	public int gangIdByName(String name) {
		GangMembershipView current = this.view;
		return current == null || name == null ? -1 : current.gangIdByName(name);
	}

	/**
	 * @return the members of the gang; empty without a view.
	 *
	 * @since gangland-api 2.6
	 */
	public List<UUID> membersOf(int gangId) {
		GangMembershipView current = this.view;
		return current == null ? Collections.emptyList() : current.membersOf(gangId);
	}

	/**
	 * @return the gang's owner; empty without a view.
	 *
	 * @since gangland-api 2.6
	 */
	public Optional<UUID> ownerOf(int gangId) {
		GangMembershipView current = this.view;
		return current == null ? Optional.empty() : current.ownerOf(gangId);
	}

	/**
	 * @return the channel the player's chat goes to; {@link GangChatChannel#PUBLIC} without a view.
	 *
	 * @since gangland-api 2.6
	 */
	public GangChatChannel chatChannelOf(UUID player) {
		GangMembershipView current = this.view;
		return current == null || player == null ? GangChatChannel.PUBLIC : current.chatChannelOf(player);
	}
}
