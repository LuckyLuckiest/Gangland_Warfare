package org.luckyraven.gangland.gang.combat;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.combat.region.FriendlyFireRegionSource;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The policy gathers the facts the pure rule needs: gangs, the gang switch, both alliance rows, world and region. */
@DisplayName("FriendlyFirePolicy - gathers the rule's inputs")
class FriendlyFirePolicyTest {

	private final MemberManager memberManager = mock(MemberManager.class);
	private final GangManager   gangManager   = mock(GangManager.class);
	private final GangRules     rules         = mock(GangRules.class);
	private final GangWorldPolicy worldPolicy = mock(GangWorldPolicy.class);
	private final UUID          a             = UUID.randomUUID();
	private final UUID          b             = UUID.randomUUID();
	private Gang                one;
	private Gang                two;
	private final Member        ma            = new Member(a);
	private final Member        mb            = new Member(b);

	private FriendlyFireRegionSource region = FriendlyFireRegionSource.NONE;

	@BeforeEach
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		one = new Gang(1);
		two = new Gang(2);
		when(worldPolicy.enabledIn(any())).thenReturn(true);
		when(rules.getFriendlyFireGlobal()).thenReturn("GANG_CHOICE");
		when(rules.isFriendlyFireAllowSelfDamage()).thenReturn(true);
		when(rules.friendlyFireWorld(any())).thenReturn(Optional.empty());
		when(memberManager.getMember(a)).thenReturn(ma);
		when(memberManager.getMember(b)).thenReturn(mb);
		when(gangManager.getGang(1)).thenReturn(one);
		when(gangManager.getGang(2)).thenReturn(two);
		ma.setGangId(1);
		mb.setGangId(1);
	}

	private FriendlyFirePolicy policy() {
		return new FriendlyFirePolicy(memberManager, gangManager, rules, worldPolicy, at -> region.allowed(at), null);
	}

	@Test
	@DisplayName("gangmates are protected until their gang turns pvp on")
	void gangSwitch() {
		assertTrue(policy().pvpBlocked(a, b, null));

		one.setFriendlyFire(true);
		assertFalse(policy().pvpBlocked(a, b, null));
	}

	@Test
	@DisplayName("your own splash or arrow is yours to take")
	void selfDamage() {
		assertFalse(policy().pvpBlocked(a, a, null));

		when(rules.isFriendlyFireAllowSelfDamage()).thenReturn(false);
		assertTrue(policy().pvpBlocked(a, a, null));
	}

	@Test
	@DisplayName("allies need both rows on; a side with no row of its own counts as off")
	void allyRows() {
		mb.setGangId(2);
		GangAlliance mine = new GangAlliance(one, two, 1L);
		one.addAlly(mine);
		assertTrue(policy().pvpBlocked(a, b, null), "only one side lists the alliance");

		mine.setFriendlyFire(true);
		assertTrue(policy().pvpBlocked(a, b, null), "the other side has no row");

		GangAlliance theirs = new GangAlliance(two, one, 1L, true);
		two.addAlly(theirs);
		assertFalse(policy().pvpBlocked(a, b, null), "both rows on");

		when(rules.getFriendlyFireGlobal()).thenReturn("OFF");
		assertTrue(policy().pvpBlocked(a, b, null), "Global OFF");
	}

	@Test
	@DisplayName("rivals, gangless players and disabled worlds are never blocked")
	void notBlocked() {
		mb.setGangId(2);
		assertFalse(policy().pvpBlocked(a, b, null), "rivals");
		assertFalse(policy().pvpBlocked(a, UUID.randomUUID(), null), "gangless victim");

		mb.setGangId(1);
		World world = mock(World.class);
		when(world.getName()).thenReturn("Peace");
		when(worldPolicy.enabledIn(world)).thenReturn(false);
		assertFalse(policy().pvpBlocked(a, b, new Location(world, 0, 0, 0)), "gangs off in that world");
	}

	@Test
	@DisplayName("a per-world ALLOW and a region verdict come before the gang switch")
	void worldAndRegion() {
		World world = mock(World.class);
		when(world.getName()).thenReturn("Arena");
		Location at = new Location(world, 0, 0, 0);

		when(rules.friendlyFireWorld("Arena")).thenReturn(Optional.of(true));
		assertFalse(policy().pvpBlocked(a, b, at), "world ALLOW");

		when(rules.friendlyFireWorld("Arena")).thenReturn(Optional.empty());
		region = loc -> Optional.of(true);
		assertFalse(policy().pvpBlocked(a, b, at), "region ALLOW");
	}

	@Test
	@DisplayName("a missing gang record (GR-15) never throws and only means not allied")
	void missingGangRecord() {
		mb.setGangId(3);
		when(gangManager.getGang(3)).thenReturn(null);

		assertFalse(policy().pvpBlocked(a, b, null));
	}
}
