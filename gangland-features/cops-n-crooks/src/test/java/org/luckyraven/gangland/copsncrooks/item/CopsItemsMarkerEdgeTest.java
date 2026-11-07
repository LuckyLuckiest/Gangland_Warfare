package org.luckyraven.gangland.copsncrooks.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The edges of {@link CopsItems#isScanner} / {@link CopsItems#isScannerMap}: a stack that claims item meta but returns
 * none, a stack without meta (never asked for it), and meta that carries no marker.
 */
@DisplayName("CopsItems - marker edge cases")
class CopsItemsMarkerEdgeTest {

	private CopsItems items;

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	@BeforeEach
	void setUp() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getName()).thenReturn("gangland");
		items = new CopsItems(plugin, () -> null);
	}

	@Test
	@DisplayName("a stack that has meta but returns none is not marked")
	void hasMetaButNullMeta_notMarked() {
		ItemStack stack = mock(ItemStack.class);
		when(stack.hasItemMeta()).thenReturn(true);
		when(stack.getItemMeta()).thenReturn(null);

		assertFalse(items.isScanner(stack));
		assertFalse(items.isScannerMap(stack));
	}

	@Test
	@DisplayName("a stack without meta is not marked and its meta is never fetched")
	void noMeta_notMarked_metaNotFetched() {
		ItemStack stack = mock(ItemStack.class);
		when(stack.hasItemMeta()).thenReturn(false);

		assertFalse(items.isScanner(stack));
		assertFalse(items.isScannerMap(stack));
		verify(stack, never()).getItemMeta();
	}

	@Test
	@DisplayName("meta whose container holds no marker is not marked")
	void metaWithoutMarker_notMarked() {
		ItemStack                stack = mock(ItemStack.class);
		ItemMeta                 meta  = mock(ItemMeta.class);
		PersistentDataContainer  data  = mock(PersistentDataContainer.class);
		when(stack.hasItemMeta()).thenReturn(true);
		when(stack.getItemMeta()).thenReturn(meta);
		when(meta.getPersistentDataContainer()).thenReturn(data);
		when(data.get(any(), eq(PersistentDataType.STRING))).thenReturn(null);

		assertFalse(items.isScanner(stack));
		assertFalse(items.isScannerMap(stack));
	}
}
