package org.luckyraven.gangland.listener.gang.combat;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.file.GangRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PotionFriendlyFireListener (GP-19)")
class PotionFriendlyFireListenerTest {

	private final PotionEffectType harmful = mock(PotionEffectType.class);
	private final PotionEffectType healing = mock(PotionEffectType.class);

	private final FriendlyFirePolicy policy   = mock(FriendlyFirePolicy.class);
	private final GangRules          rules    = mock(GangRules.class);
	private final UUID               thrower  = UUID.randomUUID();
	private final UUID               victimId = UUID.randomUUID();
	private final Player             victim   = mock(Player.class);
	private final Player             attacker = mock(Player.class);

	private PotionFriendlyFireListener listener;

	@BeforeEach
	void setUp() {
		when(attacker.getUniqueId()).thenReturn(thrower);
		when(victim.getUniqueId()).thenReturn(victimId);
		when(policy.pvpBlocked(any(), any(), any())).thenReturn(true);
		when(rules.getFriendlyFirePotions()).thenReturn("NEGATIVE_ONLY");
		Set<PotionEffectType> debuffs = Set.of(harmful);
		listener = new PotionFriendlyFireListener(policy, rules, debuffs::contains);
	}

	private PotionSplashEvent splash(PotionEffectType type) {
		ThrownPotion potion = mock(ThrownPotion.class);
		when(potion.getShooter()).thenReturn(attacker);
		PotionEffect effect = mock(PotionEffect.class);
		when(effect.getType()).thenReturn(type);
		when(potion.getEffects()).thenReturn(List.of(effect));
		PotionSplashEvent event = mock(PotionSplashEvent.class);
		when(event.getEntity()).thenReturn(potion);
		when(event.getAffectedEntities()).thenReturn(Set.<LivingEntity>of(victim));
		return event;
	}

	@Test
	@DisplayName("negativeOnly_stripsHarmful: a harmful splash is stripped for a protected friend")
	void negativeOnly_stripsHarmful() {
		PotionSplashEvent event = splash(harmful);

		listener.onSplash(event);

		verify(event).setIntensity(victim, 0D);
	}

	@Test
	@DisplayName("negativeOnly_healingReaches: a healing splash still reaches a protected friend")
	void negativeOnly_healingReaches() {
		PotionSplashEvent event = splash(healing);

		listener.onSplash(event);

		verify(event, never()).setIntensity(any(LivingEntity.class), org.mockito.ArgumentMatchers.anyDouble());
	}

	@Test
	@DisplayName("all_stripsEverything: Potions ALL strips even a healing splash")
	void all_stripsEverything() {
		when(rules.getFriendlyFirePotions()).thenReturn("ALL");
		PotionSplashEvent event = splash(healing);

		listener.onSplash(event);

		verify(event).setIntensity(victim, 0D);
	}

	@Test
	@DisplayName("an unprotected victim (rival, or the rule says no) is untouched")
	void unprotected_untouched() {
		when(policy.pvpBlocked(any(), any(), any())).thenReturn(false);
		PotionSplashEvent event = splash(harmful);

		listener.onSplash(event);

		verify(event, never()).setIntensity(any(LivingEntity.class), org.mockito.ArgumentMatchers.anyDouble());
	}

	@Test
	@DisplayName("a lingering cloud drops a protected friend from its affected set only for harmful effects")
	void cloud_dropsProtectedVictim() {
		AreaEffectCloud cloud = mock(AreaEffectCloud.class);
		when(cloud.getSource()).thenReturn(attacker);
		PotionEffect bad = mock(PotionEffect.class);
		when(bad.getType()).thenReturn(harmful);
		when(cloud.getCustomEffects()).thenReturn(List.of(bad));

		List<LivingEntity> affected = new ArrayList<>(List.of(victim));
		AreaEffectCloudApplyEvent event = mock(AreaEffectCloudApplyEvent.class);
		when(event.getEntity()).thenReturn(cloud);
		when(event.getAffectedEntities()).thenReturn(affected);

		listener.onCloud(event);

		assertEquals(0, affected.size());

		PotionEffect good = mock(PotionEffect.class);
		when(good.getType()).thenReturn(healing);
		when(cloud.getCustomEffects()).thenReturn(List.of(good));
		affected.add(victim);

		listener.onCloud(event);

		assertEquals(1, affected.size());
	}
}
