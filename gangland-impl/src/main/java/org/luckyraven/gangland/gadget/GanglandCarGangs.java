package org.luckyraven.gangland.gadget;

import org.luckyraven.gangland.gadget.car.access.CarGangContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;

import java.util.UUID;

/**
 * {@link CarGangContract} backed by the cached {@link MemberManager}. Two players share a gang when both have a
 * cached member row, both are in a gang, and the gang ids match.
 *
 * <p>Supports docket GD-06.
 */
public class GanglandCarGangs implements CarGangContract {

	private final MemberManager memberManager;

	public GanglandCarGangs(MemberManager memberManager) {
		this.memberManager = memberManager;
	}

	@Override
	public boolean sharesGang(UUID one, UUID other) {
		if (one == null || other == null) return false;
		if (one.equals(other)) return true;

		Member first  = memberManager.getMember(one);
		Member second = memberManager.getMember(other);

		if (first == null || second == null) return false;
		if (!first.hasGang() || !second.hasGang()) return false;

		return first.getGangId() == second.getGangId();
	}

}
