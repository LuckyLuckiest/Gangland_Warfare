package org.luckyraven.gangland.turf.npc.view;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.BorderComponent;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@DisplayName("MenuStyle - border and fill for the Quartermaster panels")
class MenuStyleTest {

	private BukkitStatics bukkit;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		bukkit.statics().when(Bukkit::getBukkitVersion).thenReturn("1.16.5-R0.1-SNAPSHOT");
		bukkit.statics().when(Bukkit::getVersion).thenReturn("git-Spigot-test (MC: 1.16.5)");
		ItemFactory factory = mock(ItemFactory.class);
		ItemMeta    meta    = mock(ItemMeta.class);
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
		ChestMenuBuilder builder = mock(ChestMenuBuilder.class);

		new MenuStyle("STONE", "DIRT").decorate(builder);

		verify(builder).border(any(BorderComponent.class));
		verify(builder).fill(any(FillComponent.class));
	}

	@Test
	void decorate_badName_fallsBack() {
		assertEquals(Material.BLACK_STAINED_GLASS_PANE,
		             MenuStyle.material("NOT_A_MATERIAL", Material.BLACK_STAINED_GLASS_PANE));
		assertEquals(Material.STONE, MenuStyle.material("STONE", Material.BLACK_STAINED_GLASS_PANE));

		ChestMenuBuilder builder = mock(ChestMenuBuilder.class);
		new MenuStyle("NOPE", "NADA").decorate(builder);

		verify(builder).border(any(BorderComponent.class));
		verify(builder).fill(any(FillComponent.class));
	}
}
