package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import com.cryptomorin.xseries.XAttribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CopNpcFactory.applyHealthBonus - CopTierConfig.health() reaches the spawned cop entity")
class CopNpcFactoryHealthTest {

	@Test
	@DisplayName("sets the cop's max-health attribute base value and current health to the tier's configured health")
	void applyHealthBonus_setsMaxHealthAttributeAndCurrentHealth() {
		LivingEntity      entity    = mock(LivingEntity.class);
		AttributeInstance maxHealth = mock(AttributeInstance.class);
		when(entity.getAttribute(XAttribute.MAX_HEALTH.get())).thenReturn(maxHealth);

		CopNpcFactory.applyHealthBonus(entity, 40.0);

		verify(maxHealth).setBaseValue(40.0);
		verify(entity).setHealth(40.0);
	}

	@Test
	@DisplayName("a missing max-health attribute still sets current health, and a non-living entity is a no-op")
	void applyHealthBonus_handlesMissingAttributeAndNonLivingEntity() {
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getAttribute(XAttribute.MAX_HEALTH.get())).thenReturn(null);

		CopNpcFactory.applyHealthBonus(entity, 25.0);

		verify(entity).setHealth(25.0);

		// non-living entity: must not throw
		CopNpcFactory.applyHealthBonus(mock(org.bukkit.entity.Entity.class), 25.0);
	}
}
