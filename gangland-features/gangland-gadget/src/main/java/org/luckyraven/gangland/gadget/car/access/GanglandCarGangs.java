package org.luckyraven.gangland.gadget.car.access;

import org.luckyraven.gangland.data.gang.GangMembership;

import java.util.UUID;

/**
 * {@link CarGangContract} backed by the always-present {@link GangMembership} holder (WS5 G2 S1) — gadget stays
 * gang-module-free, no {@code Depends: [gang]} edge. Two players share a gang when both are in <em>the same</em>
 * gang (deliberately not {@code alliedOrSame}: GD-06 pins "shares a gang", not "same or allied").
 *
 * <p>Supports docket GD-06.
 */
public class GanglandCarGangs implements CarGangContract {

	private final GangMembership membership;

	public GanglandCarGangs(GangMembership membership) {
		this.membership = membership;
	}

	@Override
	public boolean sharesGang(UUID one, UUID other) {
		if (one == null || other == null) return false;
		if (one.equals(other)) return true;

		int gangOne = membership.gangIdOf(one);
		if (gangOne == -1) return false;

		return gangOne == membership.gangIdOf(other);
	}

}
