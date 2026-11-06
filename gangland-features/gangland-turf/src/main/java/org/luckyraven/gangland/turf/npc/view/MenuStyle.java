package org.luckyraven.gangland.turf.npc.view;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.BorderComponent;
import org.luckyraven.keystone.inventory.component.FillComponent;

/** The Quartermaster panels' outer ring and empty-slot item, from {@code turf_settings.yml} {@code Menu}. */
public record MenuStyle(String borderItem, String fillItem) {

	public static final MenuStyle DEFAULT = new MenuStyle("BLACK_STAINED_GLASS_PANE", "GRAY_STAINED_GLASS_PANE");

	public void decorate(ChestMenuBuilder builder) {
		builder.border(BorderComponent.of(material(borderItem, Material.BLACK_STAINED_GLASS_PANE)).name(" "));
		builder.fill(FillComponent.of(material(fillItem, Material.GRAY_STAINED_GLASS_PANE)).name(" "));
	}

	static Material material(String name, Material fallback) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(fallback);
	}
}
