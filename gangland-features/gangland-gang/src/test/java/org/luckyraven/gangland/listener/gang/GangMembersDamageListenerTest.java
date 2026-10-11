package org.luckyraven.gangland.listener.gang;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.combat.region.FriendlyFireRegionSource;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GR-15: a member whose gang record is missing must not NPE the friendly-fire check. GR-39: a member's own arrow hits
 * him again. GR-41: fire set by a gangmate (Flame, Fire Aspect) follows the same rule.
 */
@DisplayName("GangMembersDamageListener")
class GangMembersDamageListenerTest {

	private final MemberManager   memberManager = mock(MemberManager.class);
	private final GangManager     gangManager   = mock(GangManager.class);
	private final GangRules       rules         = mock(GangRules.class);
	private final GangWorldPolicy worldPolicy   = mock(GangWorldPolicy.class);

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		when(worldPolicy.enabledIn(any())).thenReturn(true);
		when(rules.getFriendlyFireGlobal()).thenReturn("GANG_CHOICE");
		when(rules.isFriendlyFireAllowSelfDamage()).thenReturn(true);
	}

	private GangMembersDamageListener listener() {
		return new GangMembersDamageListener(new FriendlyFirePolicy(memberManager, gangManager, rules, worldPolicy,
		                                                            FriendlyFireRegionSource.NONE, null));
	}

	private Player member(int gangId) {
		UUID   id     = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		Member member = new Member(id);
		member.setGangId(gangId);
		when(memberManager.getMember(id)).thenReturn(member);
		return player;
	}

	@Test
	@DisplayName("damage_whenGangRecordMissing_noNpe: one side's gang id has no Gang, the hit is not cancelled")
	void damage_whenGangRecordMissing_noNpe() {
		Player damager = member(1);
		Player damaged = member(2);
		Gang   gang    = new Gang(1);
		gang.addAlly(new Gang(3));
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);

		when(event.getDamager()).thenReturn(damager);
		when(event.getEntity()).thenReturn(damaged);
		when(gangManager.getGang(1)).thenReturn(gang);
		when(gangManager.getGang(2)).thenReturn(null);

		assertDoesNotThrow(() -> listener().onGangMemberHitMembers(event));
		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("selfShot_allowedByDefault: a member's own arrow is not cancelled (GR-39)")
	void selfShot_allowedByDefault() {
		Player me    = member(1);
		Arrow  arrow = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn(me);
		when(gangManager.getGang(1)).thenReturn(new Gang(1));
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(arrow);
		when(event.getEntity()).thenReturn(me);

		listener().onGangMemberHitMembers(event);

		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("Allow_Self_Damage false restores the old block on a member's own arrow")
	void selfShot_blockedWhenConfigured() {
		when(rules.isFriendlyFireAllowSelfDamage()).thenReturn(false);
		Player me    = member(1);
		Arrow  arrow = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn(me);
		when(gangManager.getGang(1)).thenReturn(new Gang(1));
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(arrow);
		when(event.getEntity()).thenReturn(me);

		listener().onGangMemberHitMembers(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("a gangmate's arrow is still cancelled while gang pvp is off, and lands once it is on")
	void gangmateArrow() {
		Player shooter = member(1);
		Player victim  = member(1);
		Arrow  arrow   = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn(shooter);
		Gang gang = new Gang(1);
		when(gangManager.getGang(1)).thenReturn(gang);
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(arrow);
		when(event.getEntity()).thenReturn(victim);

		listener().onGangMemberHitMembers(event);
		verify(event).setCancelled(true);

		gang.setFriendlyFire(true);
		EntityDamageByEntityEvent second = mock(EntityDamageByEntityEvent.class);
		when(second.getDamager()).thenReturn(arrow);
		when(second.getEntity()).thenReturn(victim);

		listener().onGangMemberHitMembers(second);
		verify(second, never()).setCancelled(true);
	}

	@Test
	@DisplayName("combustBetweenGangmates_cancelled: Flame and Fire Aspect are stopped at the source (GR-41)")
	void combustBetweenGangmates_cancelled() {
		Player shooter = member(1);
		Player victim  = member(1);
		Arrow  arrow   = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn(shooter);
		when(gangManager.getGang(1)).thenReturn(new Gang(1));
		EntityCombustByEntityEvent event = mock(EntityCombustByEntityEvent.class);
		when(event.getCombuster()).thenReturn(arrow);
		when(event.getEntity()).thenReturn(victim);

		listener().onCombust(event);

		verify(event).setCancelled(true);
	}
}
