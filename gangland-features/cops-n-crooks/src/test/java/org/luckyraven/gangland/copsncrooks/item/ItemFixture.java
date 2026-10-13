package org.luckyraven.gangland.copsncrooks.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test support: a Bukkit with an {@link ItemFactory} whose {@link ItemMeta} is a stateful fake (display name, lore and
 * a persistent data container that remembers what was set). Every meta is its own instance, so a plain item never
 * carries another item's marker. Close it in {@code @AfterEach}; call {@code BukkitRegistryFixture.install()} once in
 * {@code @BeforeAll} first.
 */
final class ItemFixture implements AutoCloseable {

	private final BukkitStatics bukkit = BukkitStatics.install();

	ItemFixture() {
		ItemFactory factory = mock(ItemFactory.class);

		when(factory.getItemMeta(any(Material.class))).thenAnswer(invocation -> newMeta());
		when(factory.isApplicable(any(ItemMeta.class), any(Material.class))).thenReturn(true);
		when(factory.asMetaFor(any(ItemMeta.class), any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
		// ItemStack.setItemMeta asks the factory for the (possibly changed) material: echo the one it has
		when(factory.updateMaterial(any(ItemMeta.class), any(Material.class))).thenAnswer(invocation -> invocation.getArgument(1));
		bukkit.statics().when(Bukkit::getItemFactory).thenReturn(factory);
	}

	BukkitStatics bukkit() {
		return bukkit;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static ItemMeta newMeta() {
		ItemMeta                 meta   = mock(ItemMeta.class);
		PersistentDataContainer  pdc    = mock(PersistentDataContainer.class);
		Map<NamespacedKey, Object> store = new HashMap<>();
		String[]                 name   = {null};
		List<String>[]           lore   = new List[]{null};

		doAnswer(invocation -> {
			store.put(invocation.getArgument(0), invocation.getArgument(2));
			return null;
		}).when(pdc).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
		when(pdc.has(any(NamespacedKey.class), any(PersistentDataType.class)))
				.thenAnswer(invocation -> store.containsKey((NamespacedKey) invocation.getArgument(0)));
		when(pdc.get(any(NamespacedKey.class), any(PersistentDataType.class)))
				.thenAnswer(invocation -> store.get((NamespacedKey) invocation.getArgument(0)));

		when(meta.getPersistentDataContainer()).thenReturn(pdc);
		when(meta.clone()).thenReturn(meta);
		doAnswer(invocation -> {
			name[0] = invocation.getArgument(0);
			return null;
		}).when(meta).setDisplayName(any());
		when(meta.getDisplayName()).thenAnswer(invocation -> name[0]);
		when(meta.hasDisplayName()).thenAnswer(invocation -> name[0] != null);
		doAnswer(invocation -> {
			lore[0] = invocation.getArgument(0) == null ? null : new ArrayList<>((List<String>) invocation.getArgument(0));
			return null;
		}).when(meta).setLore(any());
		when(meta.getLore()).thenAnswer(invocation -> lore[0]);
		when(meta.hasLore()).thenAnswer(invocation -> lore[0] != null);

		return meta;
	}

	@Override
	public void close() {
		bukkit.close();
	}
}
