package org.luckyraven.gangland.gang.combat;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.combat.region.FriendlyFireRegionSource;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Whether one player may hurt another: gathers the facts (gangs, alliance rows, world, region, rules) and hands them to
 * the pure {@link FriendlyFireRule}. It reads the managers directly (never the {@code GangMembership} holder), because
 * the holder's view delegates here. The gang pvp commands read their members and texts through it too, so the command
 * constructors stay as the kickoff wired them.
 */
public class FriendlyFirePolicy {

	private final MemberManager            memberManager;
	private final GangManager              gangManager;
	private final GangRules                rules;
	private final GangWorldPolicy          worldPolicy;
	private final FriendlyFireRegionSource regionSource;
	private final GangMessages             messages;

	public FriendlyFirePolicy(MemberManager memberManager, GangManager gangManager, GangRules rules,
	                          GangWorldPolicy worldPolicy, FriendlyFireRegionSource regionSource,
	                          GangMessages messages) {
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.rules         = rules;
		this.worldPolicy   = worldPolicy;
		this.regionSource  = regionSource;
		this.messages      = messages;
	}

	public boolean pvpBlocked(@Nullable UUID attacker, @Nullable UUID victim, @Nullable Location at) {
		int attackerGang = gangIdOf(attacker);
		int victimGang   = gangIdOf(victim);

		if (attackerGang == -1 || victimGang == -1) return false;

		World world = at == null ? null : at.getWorld();
		Gang  a     = gangManager.getGang(attackerGang);
		Gang  b     = attackerGang == victimGang ? a : gangManager.getGang(victimGang);

		// GR-15: a missing gang record only ever means "not allied"
		boolean allied = attackerGang != victimGang && a != null && b != null && a.isAlly(b);

		Optional<Boolean> region    = at == null ? Optional.empty() : regionSource.allowed(at);
		Optional<Boolean> worldRule = world == null ? Optional.empty() : rules.friendlyFireWorld(world.getName());

		boolean gangFriendlyFire = a != null && a.isFriendlyFire();
		boolean allyFriendlyFire = allied && row(a, b) && row(b, a);

		return FriendlyFireRule.blocked(attackerGang, victimGang, allied, attacker.equals(victim),
		                                !worldPolicy.enabledIn(world), region, worldRule,
		                                FriendlyFireRule.Global.parse(rules.getFriendlyFireGlobal()),
		                                gangFriendlyFire, allyFriendlyFire, rules.isFriendlyFireAllowSelfDamage());
	}

	/** Whether {@code from} lets {@code to} hurt it; a side with no row of its own says no. */
	public static boolean row(Gang from, Gang to) {
		GangAlliance alliance = findRow(from, to);
		return alliance != null && alliance.isFriendlyFire();
	}

	/** {@code from}'s own alliance row towards {@code to}, or {@code null} when only {@code to} lists the alliance. */
	@Nullable
	public static GangAlliance findRow(Gang from, Gang to) {
		for (GangAlliance alliance : from.getAllies()) {
			if (alliance.ally().getId() == to.getId()) return alliance;
		}
		return null;
	}

	@Nullable
	public Member memberOf(UUID player) {
		return memberManager.getMember(player);
	}

	public GangMessages messages() {
		return messages;
	}

	private int gangIdOf(@Nullable UUID uuid) {
		if (uuid == null) return -1;

		Member member = memberManager.getMember(uuid);
		return member == null ? -1 : member.getGangId();
	}
}
