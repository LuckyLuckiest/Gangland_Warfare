package org.luckyraven.gangland.turf.powerups;

import com.cryptomorin.xseries.XAttribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@DisplayName("GuardBuffs - the pure rules behind the three guard powerups")
class GuardBuffsTest {

	/** A living entity whose max-health attribute follows its setter, like the real thing. */
	private static LivingEntity entity(double maxBase, double health, AttributeInstance[] holder) {
		LivingEntity      entity = mock(LivingEntity.class);
		AttributeInstance attr   = mock(AttributeInstance.class);
		double[]          base   = {maxBase};
		when(attr.getBaseValue()).thenAnswer(invocation -> base[0]);
		doAnswer(invocation -> {
			base[0] = invocation.getArgument(0);
			return null;
		}).when(attr).setBaseValue(anyDouble());
		when(entity.getAttribute(XAttribute.MAX_HEALTH.get())).thenReturn(attr);
		when(entity.getHealth()).thenReturn(health);
		holder[0] = attr;
		return entity;
	}

	@Test
	void up_clampsAndRejectsNan() {
		assertEquals(1.5, GuardBuffs.up(1.5, 3));
		assertEquals(3, GuardBuffs.up(5, 3));
		assertEquals(1, GuardBuffs.up(0.5, 3));
		assertEquals(1, GuardBuffs.up(Double.NaN, 3));
		assertEquals(1, GuardBuffs.up(Double.POSITIVE_INFINITY, 3));
	}

	@Test
	void wait_clampsAndRejectsNan() {
		assertEquals(0.5, GuardBuffs.wait(0.5, 0.25));
		assertEquals(0.25, GuardBuffs.wait(0.1, 0.25));
		assertEquals(1, GuardBuffs.wait(0, 0.25));
		assertEquals(1, GuardBuffs.wait(1.5, 0.25));
		assertEquals(1, GuardBuffs.wait(Double.NaN, 0.25));
	}

	@Test
	void scale_neverBelowOne() {
		assertEquals(5, GuardBuffs.scale(3, 1.5));
		assertEquals(1, GuardBuffs.scale(1, 0.1));
		assertEquals(Integer.MAX_VALUE, GuardBuffs.scale(6, 1e10), "past int range it caps, never wraps");
	}

	@Test
	void applyHealth_noAttribute_noThrow() {
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getAttribute(XAttribute.MAX_HEALTH.get())).thenReturn(null);

		GuardBuffs.applyHealth(entity, 20, 1.5);

		verify(entity, never()).setHealth(anyDouble());
		assertEquals(-1, GuardBuffs.maxHealth(entity));
	}

	@Test
	void applyHealth_scalesHealthShare() {
		AttributeInstance[] attr   = new AttributeInstance[1];
		LivingEntity        entity = entity(20, 10, attr);

		GuardBuffs.applyHealth(entity, 20, 1.5);

		verify(attr[0]).setBaseValue(30.0);
		verify(entity).setHealth(15.0);
	}

	@Test
	void applyHealth_fallback_neverKills() {
		AttributeInstance[] attr   = new AttributeInstance[1];
		LivingEntity        entity = entity(60, 1, attr);

		GuardBuffs.applyHealth(entity, 20, 1.0);

		verify(attr[0]).setBaseValue(20.0);
		verify(entity).setHealth(1.0);
		verify(entity, never()).setHealth(0.0);
	}

	@Test
	void applyHealth_capped_fallsBackToBase() {
		AttributeInstance[] attr   = new AttributeInstance[1];
		LivingEntity        entity = entity(20, 20, attr);

		GuardBuffs.applyHealth(entity, 500, 3);
		assertEquals(1024.0, attr[0].getBaseValue());

		GuardBuffs.applyHealth(entity, 500, 1.0);
		assertEquals(500.0, attr[0].getBaseValue());
	}
}
