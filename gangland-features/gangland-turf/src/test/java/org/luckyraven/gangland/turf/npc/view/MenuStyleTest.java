package org.luckyraven.gangland.turf.npc.view;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.BorderComponent;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.RenderContext;
import org.luckyraven.keystone.inventory.component.SlotView;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MenuStyle - border and fill for the Quartermaster panels")
class MenuStyleTest {

	private BukkitStatics bukkit;
	private ItemMeta       meta;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		bukkit.statics().when(Bukkit::getBukkitVersion).thenReturn("1.16.5-R0.1-SNAPSHOT");
		bukkit.statics().when(Bukkit::getVersion).thenReturn("git-Spigot-test (MC: 1.16.5)");
		ItemFactory factory = mock(ItemFactory.class);
		meta = mock(ItemMeta.class);
		when(factory.getItemMeta(any())).thenReturn(meta);
		when(factory.asMetaFor(any(), any(Material.class))).thenReturn(meta);
		bukkit.statics().when(Bukkit::getItemFactory).thenReturn(factory);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	void decorate_setsBorderAndFill() {
		assertEquals(List.of(Material.STONE, Material.DIRT), rendered(new MenuStyle("STONE", "DIRT")));
		verify(meta, times(2)).setDisplayName(" ");
	}

	@Test
	void decorate_badName_fallsBack() {
		assertEquals(Material.BLACK_STAINED_GLASS_PANE,
		             MenuStyle.material("NOT_A_MATERIAL", Material.BLACK_STAINED_GLASS_PANE));
		assertEquals(Material.STONE, MenuStyle.material("STONE", Material.BLACK_STAINED_GLASS_PANE));

		assertEquals(List.of(Material.BLACK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE),
		             rendered(new MenuStyle("NOPE", "NADA")));
	}

	/** The border and fill materials {@code decorate} hands the builder, in that order. */
	private List<Material> rendered(MenuStyle style) {
		ChestMenuBuilder builder = mock(ChestMenuBuilder.class);
		style.decorate(builder);

		ArgumentCaptor<BorderComponent> border = ArgumentCaptor.forClass(BorderComponent.class);
		ArgumentCaptor<FillComponent>   fill   = ArgumentCaptor.forClass(FillComponent.class);
		verify(builder).border(border.capture());
		verify(builder).fill(fill.capture());

		SlotView slot = mock(SlotView.class);
		RenderContext ctx = mock(RenderContext.class);
		border.getValue().renderInto(slot, ctx);
		fill.getValue().renderInto(slot, ctx);

		ArgumentCaptor<ItemStack> items = ArgumentCaptor.forClass(ItemStack.class);
		verify(slot, times(2)).setItem(items.capture());
		return items.getAllValues().stream().map(ItemStack::getType).toList();
	}
}
