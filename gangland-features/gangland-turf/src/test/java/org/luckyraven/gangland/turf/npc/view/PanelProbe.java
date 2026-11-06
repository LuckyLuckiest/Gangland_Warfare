package org.luckyraven.gangland.turf.npc.view;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.Component;
import org.luckyraven.keystone.inventory.component.RenderContext;
import org.luckyraven.keystone.inventory.component.SlotView;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Renders a Quartermaster panel against a mocked builder and keeps what it drew: the explicit slots as real item
 * stacks (material, name, lore), the row count and whether the border and fill were set. Close it after the test.
 */
final class PanelProbe implements AutoCloseable {

	private final BukkitStatics            bukkit;
	final         Map<Integer, ItemStack>  items = new TreeMap<>();
	int     rows;
	boolean border;
	boolean fill;

	PanelProbe(Panel<TurfPowerupFlowSession> panel, TurfPowerupFlowSession session) {
		bukkit = BukkitStatics.install();
		bukkit.statics().when(Bukkit::getBukkitVersion).thenReturn("1.16.5-R0.1-SNAPSHOT");
		bukkit.statics().when(Bukkit::getVersion).thenReturn("git-Spigot-test (MC: 1.16.5)");
		ItemFactory factory = mock(ItemFactory.class);
		when(factory.getItemMeta(any())).thenAnswer(invocation -> meta());
		when(factory.isApplicable(any(ItemMeta.class), any(Material.class))).thenReturn(true);
		when(factory.asMetaFor(any(ItemMeta.class), any(Material.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(factory.updateMaterial(any(ItemMeta.class), any(Material.class)))
				.thenAnswer(invocation -> invocation.getArgument(1));
		bukkit.statics().when(Bukkit::getItemFactory).thenReturn(factory);

		try {
			draw(panel, session);
		} catch (RuntimeException | Error failure) {
			bukkit.close();
			throw failure;
		}
	}

	@SuppressWarnings("unchecked")
	private void draw(Panel<TurfPowerupFlowSession> panel, TurfPowerupFlowSession session) {
		rows = panel.rows(session);
		ChestMenuBuilder builder = mock(ChestMenuBuilder.class);
		panel.render(mock(MenuFlow.class), builder, session);

		ArgumentCaptor<Integer>      slots      = ArgumentCaptor.forClass(Integer.class);
		ArgumentCaptor<Component>    components = ArgumentCaptor.forClass(Component.class);
		org.mockito.Mockito.verify(builder, atLeastOnce()).slot(slots.capture(), components.capture());
		for (int i = 0; i < slots.getAllValues().size(); i++) {
			SlotView slot = mock(SlotView.class);
			components.getAllValues().get(i).renderInto(slot, mock(RenderContext.class));
			ArgumentCaptor<ItemStack> stack = ArgumentCaptor.forClass(ItemStack.class);
			org.mockito.Mockito.verify(slot).setItem(stack.capture());
			items.put(slots.getAllValues().get(i), stack.getValue());
		}
		border = !org.mockito.Mockito.mockingDetails(builder).getInvocations().stream()
		                              .filter(i -> i.getMethod().getName().equals("border")).toList().isEmpty();
		fill   = !org.mockito.Mockito.mockingDetails(builder).getInvocations().stream()
		                              .filter(i -> i.getMethod().getName().equals("fill")).toList().isEmpty();
	}

	private ItemStack stack(int slot) {
		ItemStack stack = items.get(slot);
		org.junit.jupiter.api.Assertions.assertNotNull(stack, "no item on slot " + slot + ", drew " + items.keySet());
		return stack;
	}

	Material type(int slot) {
		return stack(slot).getType();
	}

	List<String> lore(int slot) {
		List<String> lore = stack(slot).getItemMeta().getLore();
		return lore == null ? List.of() : lore;
	}

	String name(int slot) {
		return stack(slot).getItemMeta().getDisplayName();
	}

	/** True when no explicit slot shows a sword, a helmet or a potion (they carry attribute text on 1.21). */
	boolean noAttributeIcons() {
		return items.values().stream().map(ItemStack::getType).map(Enum::name)
		            .noneMatch(n -> n.endsWith("_SWORD") || n.endsWith("_HELMET") || n.contains("POTION"));
	}

	private static ItemMeta meta() {
		ItemMeta                      meta = mock(ItemMeta.class);
		AtomicReference<String>       name = new AtomicReference<>();
		AtomicReference<List<String>> lore = new AtomicReference<>();
		doAnswer(i -> {
			name.set(i.getArgument(0));
			return null;
		}).when(meta).setDisplayName(any());
		doAnswer(i -> {
			lore.set(i.getArgument(0));
			return null;
		}).when(meta).setLore(any());
		when(meta.getDisplayName()).thenAnswer(i -> name.get());
		when(meta.hasDisplayName()).thenAnswer(i -> name.get() != null);
		when(meta.getLore()).thenAnswer(i -> lore.get());
		when(meta.hasLore()).thenAnswer(i -> lore.get() != null);
		when(meta.clone()).thenReturn(meta);
		return meta;
	}

	@Override
	public void close() {
		bukkit.close();
	}
}
