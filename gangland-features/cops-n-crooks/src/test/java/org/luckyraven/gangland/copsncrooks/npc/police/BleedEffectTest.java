package org.luckyraven.gangland.copsncrooks.npc.police;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npc.BleedSpot;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BleedEffect - particle resolution, severity scaling, spot picking")
class BleedEffectTest {

	@Test
	@DisplayName("1.16.5 names: BLOCK_CRACK wins; 1.20.5+ names: BLOCK is its twin; dust is the fallback")
	void resolution() {
		assertEquals("BLOCK_CRACK", BleedEffect.resolveName("BLOCK_CRACK", Set.of("BLOCK_CRACK", "REDSTONE")::contains));
		assertEquals("BLOCK", BleedEffect.resolveName("block_crack", Set.of("BLOCK", "DUST")::contains));
		assertEquals("BLOCK_CRACK", BleedEffect.resolveName("BLOCK", Set.of("BLOCK_CRACK")::contains));
		assertEquals("DUST", BleedEffect.resolveName("NOPE", Set.of("DUST")::contains));
		assertEquals("REDSTONE", BleedEffect.resolveName(null, Set.of("REDSTONE")::contains));
		assertNull(BleedEffect.resolveName("BLOCK_CRACK", n -> false));
	}

	@Test
	@DisplayName("severity runs 0 at the hurt threshold to 1 at no health; amount doubles across that band")
	void severityAndAmount() {
		assertEquals(0.0, BleedEffect.severity(10, 20, 0.5), 1e-9);
		assertEquals(0.5, BleedEffect.severity(5, 20, 0.5), 1e-9);
		assertEquals(1.0, BleedEffect.severity(0, 20, 0.5), 1e-9);
		assertEquals(0.0, BleedEffect.severity(15, 20, 0.5), 1e-9);
		assertEquals(6, BleedEffect.amount(6, 0));
		assertEquals(9, BleedEffect.amount(6, 0.5));
		assertEquals(12, BleedEffect.amount(6, 1));
	}

	@Test
	@DisplayName("a burst picks one or two distinct spots, only from the configured ones")
	void pick() {
		List<BleedSpot> allowed = List.of(BleedSpot.HEAD, BleedSpot.CHEST);
		Random random = new Random(7);
		boolean sawOne = false, sawTwo = false;
		for (int i = 0; i < 200; i++) {
			List<BleedSpot> picked = BleedEffect.pickSpots(allowed, 0.5, random);
			assertTrue(allowed.containsAll(picked));
			assertEquals(picked.size(), Set.copyOf(picked).size());
			sawOne |= picked.size() == 1;
			sawTwo |= picked.size() == 2;
		}
		assertTrue(sawOne && sawTwo);
		assertEquals(1, BleedEffect.pickSpots(List.of(BleedSpot.HEAD), 1.0, random).size());
	}

	@Test
	@DisplayName("the cadence runs from every 4th tick at the threshold down to every tick at death's door")
	void interval() {
		assertEquals(4, BleedEffect.interval(0));
		assertEquals(1, BleedEffect.interval(1));
		assertTrue(BleedEffect.interval(0.5) < 4 && BleedEffect.interval(0.5) > 1);
	}

	@Test
	@DisplayName("a particle that needs data we cannot give falls back to the block-crack blood instead of throwing")
	void dataParticle_fallsBack() {
		org.junit.jupiter.api.Assumptions.assumeTrue(
				java.util.Arrays.stream(org.bukkit.Particle.values()).anyMatch(p -> p.name().equals("ITEM_CRACK")));
		org.bukkit.World world = org.mockito.Mockito.mock(org.bukkit.World.class);
		org.bukkit.entity.LivingEntity body = org.mockito.Mockito.mock(org.bukkit.entity.LivingEntity.class);
		org.mockito.Mockito.when(body.getWorld()).thenReturn(world);
		org.mockito.Mockito.when(body.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 64, 0));
		org.mockito.Mockito.doThrow(new IllegalArgumentException("data required")).when(world).spawnParticle(
				org.mockito.ArgumentMatchers.eq(org.bukkit.Particle.valueOf("ITEM_CRACK")),
				org.mockito.ArgumentMatchers.any(org.bukkit.Location.class), org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
				org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());

		org.luckyraven.gangland.npc.BleedSettings settings = new org.luckyraven.gangland.npc.BleedSettings(
				"ITEM_CRACK", 4, List.of(BleedSpot.CHEST));
		BleedEffect.burst(body, settings, 0, 1, new Random(1));

		org.mockito.Mockito.verify(world, org.mockito.Mockito.atLeastOnce()).spawnParticle(
				org.mockito.ArgumentMatchers.argThat((org.bukkit.Particle p) -> p.name().startsWith("BLOCK")
				                                                                 || p.name().equals("REDSTONE")),
				org.mockito.ArgumentMatchers.any(org.bukkit.Location.class), org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
				org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
				org.mockito.ArgumentMatchers.any());
	}
}
