package org.luckyraven.gangland.copsncrooks.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.item.ItemConverter;
import org.luckyraven.keystone.item.spi.ItemVocabulary;
import org.luckyraven.keystone.item.spi.ItemVocabularyRegistrar;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane f2-config: namespace {@code copsncrooks} resolves {@code copsncrooks:police_scanner} and
 * {@code copsncrooks:scanner_map} in shop, trader and loot item strings (P-SCN-3, P-MAP-3), and is published on the
 * {@code ServicesManager} so {@code GanglandContext.installItemVocabularies()} finds it.
 */
@DisplayName("CopsItemVocabulary")
class CopsItemVocabularyTest {

	private BukkitStatics bukkit;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("the namespace is copsncrooks")
	void namespace() {
		assertEquals("copsncrooks", new CopsItemVocabulary(mock(CopsItems.class)).namespace());
	}

	@Test
	@DisplayName("contribute registers one converter for type copsncrooks that maps the modifier to the item")
	void contribute_registersConverter() {
		CopsItems items   = mock(CopsItems.class);
		ItemStack scanner = mock(ItemStack.class);
		ItemStack map     = mock(ItemStack.class);
		when(items.create("police_scanner")).thenReturn(scanner);
		when(items.create("scanner_map")).thenReturn(map);

		ItemVocabularyRegistrar registrar = mock(ItemVocabularyRegistrar.class);
		new CopsItemVocabulary(items).contribute(registrar);

		ArgumentCaptor<ItemConverter> converter = ArgumentCaptor.forClass(ItemConverter.class);
		verify(registrar).converter(eq("copsncrooks"), converter.capture());

		assertSame(scanner, converter.getValue().convert("copsncrooks", "police_scanner", Map.of()));
		assertSame(map, converter.getValue().convert("copsncrooks", "scanner_map", Map.of()));
		assertNull(converter.getValue().convert("copsncrooks", "jetpack", Map.of()));
		assertNull(converter.getValue().convert("copsncrooks", null, Map.of()));
	}

	@Test
	@DisplayName("publish registers the vocabulary as an ItemVocabulary service of the plugin and returns it")
	void publish_registersService() {
		JavaPlugin plugin = mock(JavaPlugin.class);

		CopsItemVocabulary vocabulary = CopsItemVocabulary.publish(plugin, mock(CopsItems.class));

		verify(bukkit.servicesManager()).register(eq(ItemVocabulary.class), eq(vocabulary), eq(plugin),
		                                          eq(ServicePriority.Normal));
		assertEquals("copsncrooks", vocabulary.namespace());
	}
}
