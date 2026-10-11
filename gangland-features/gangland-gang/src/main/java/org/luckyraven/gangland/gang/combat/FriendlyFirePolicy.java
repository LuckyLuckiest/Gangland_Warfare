package org.luckyraven.gangland.gang.combat;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;

import java.util.UUID;

/**
 * Whether one player may hurt another. Seam: lane W5-F gives it the gang pvp switches; until then it is today's rule,
 * same gang or allied gangs may not hurt each other. It reads the managers directly (never the {@code GangMembership}
 * holder), because the holder's view delegates here.
 */
public class FriendlyFirePolicy {

	private final MemberManager memberManager;
	private final GangManager   gangManager;

	public FriendlyFirePolicy(MemberManager memberManager, GangManager gangManager) {
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
	}

	public boolean pvpBlocked(UUID attacker, UUID victim, @Nullable Location at) {
		int attackerGang = gangIdOf(attacker);
		int victimGang   = gangIdOf(victim);

		if (attackerGang == -1 || victimGang == -1) return false;
		if (attackerGang == victimGang) return true;

		Gang a = gangManager.getGang(attackerGang);
		Gang b = gangManager.getGang(victimGang);
		return a != null && b != null && a.isAlly(b);
	}

	private int gangIdOf(UUID uuid) {
		if (uuid == null) return -1;

		Member member = memberManager.getMember(uuid);
		return member == null ? -1 : member.getGangId();
	}
}
