package org.luckyraven.gangland;

import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.teleportation.WaypointLookupContract;

/**
 * The only implementation of {@link GanglandApi} (WS6 G1). Thin: every accessor returns exactly the
 * constructor-injected instance, all four mandatory (R7 removed the one accessor — {@code gangs()} — that used to
 * need a lazy {@code DependencyContainer} lookup for an {@code Optional} module-owned contract; {@link
 * GangMembership} is a core bean like the other three, always present, inert until the gang module installs a
 * view). Mirrors Bartizan's {@code BartizanApiImpl} shape.
 */
public final class GanglandApiImpl implements GanglandApi {

	private final UserLookupContract      users;
	private final GangMembership          gangs;
	private final WaypointLookupContract  waypoints;
	private final BankTiers               bankTiers;

	public GanglandApiImpl(UserLookupContract users, GangMembership gangs, WaypointLookupContract waypoints,
	                       BankTiers bankTiers) {
		this.users     = users;
		this.gangs     = gangs;
		this.waypoints = waypoints;
		this.bankTiers = bankTiers;
	}

	@Override
	public UserLookupContract users() {
		return users;
	}

	@Override
	public GangMembership gangs() {
		return gangs;
	}

	@Override
	public WaypointLookupContract waypoints() {
		return waypoints;
	}

	@Override
	public BankTiers bankTiers() {
		return bankTiers;
	}

}
