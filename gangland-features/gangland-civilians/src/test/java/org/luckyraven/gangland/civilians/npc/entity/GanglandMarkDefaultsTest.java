package org.luckyraven.gangland.civilians.npc.entity;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.config.CiviliansConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansLoader;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GanglandMarkDefaults")
class GanglandMarkDefaultsTest {

	private final Locale originalLocale = Locale.getDefault();

	@AfterEach
	void tearDown() {
		Locale.setDefault(originalLocale);
	}

	@Test
	@DisplayName("CJ-34: an unknown entity name in civilians.yml is skipped, the valid names still apply")
	void defaults_unknownEntityName_isSkippedNotThrown() {
		GanglandMarkDefaults marks = marks(List.of("not_a_mob", "cow"), List.of("typo_zombie", "zombie"));

		Map<EntityType, String> defaults = assertDoesNotThrow(marks::defaults);

		assertEquals(EntityMark.CIVILIAN.name(), defaults.get(EntityType.COW));
		assertEquals(EntityMark.POLICE.name(), defaults.get(EntityType.ZOMBIE));
	}

	@Test
	@DisplayName("CJ-34: entity names are upper-cased with Locale.ROOT, so a Turkish default locale still matches")
	void defaults_turkishDefaultLocale_stillResolvesNames() {
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));
		GanglandMarkDefaults marks = marks(List.of("wither_skeleton", "iron_golem"), List.of("pillager", "villager"));

		Map<EntityType, String> defaults = assertDoesNotThrow(marks::defaults);

		assertEquals(EntityMark.CIVILIAN.name(), defaults.get(EntityType.IRON_GOLEM));
		assertEquals(EntityMark.CIVILIAN.name(), defaults.get(EntityType.WITHER_SKELETON));
		assertEquals(EntityMark.POLICE.name(), defaults.get(EntityType.PILLAGER));
		assertEquals(EntityMark.POLICE.name(), defaults.get(EntityType.VILLAGER));
	}

	private GanglandMarkDefaults marks(List<String> civilians, List<String> police) {
		CiviliansConfig config = mock(CiviliansConfig.class);
		when(config.defaultCivilianEntities()).thenReturn(civilians);
		when(config.defaultPoliceEntities()).thenReturn(police);

		CiviliansLoader loader = mock(CiviliansLoader.class);
		when(loader.getLoadedConfig()).thenReturn(config);
		return new GanglandMarkDefaults(loader);
	}
}
