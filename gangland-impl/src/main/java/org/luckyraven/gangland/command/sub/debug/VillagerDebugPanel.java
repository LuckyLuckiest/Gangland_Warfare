package org.luckyraven.gangland.command.sub.debug;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.menu.InventoryBuilder;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.FlowState;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.function.Consumer;

/**
 * Debug-only panel with a single emerald button that runs {@link #openAction} (typically opens a
 * {@code VillagerInventory}). Used by {@code DebugCommand}'s {@code villager} sub-arg to test the merchant API behind a
 * {@link MenuFlow} hop. The flow ends naturally when the merchant opens — Bukkit fires
 * {@code InventoryCloseEvent} synchronously inside {@code openMerchant}, which the host's close listener picks up and
 * runs cleanup.
 */
@RequiredArgsConstructor
public final class VillagerDebugPanel implements Panel<VillagerDebugPanel.Session> {

	private static final int              ROWS      = 3;
	private static final int              OPEN_SLOT = 13;
	private final        Consumer<Player> openAction;

	@Override
	public int rows(Session session) {
		return ROWS;
	}

	@Override
	public String title(Session session) {
		return "&6&lOpen Villager";
	}

	@Override
	public void render(MenuFlow<Session> flow, ChestMenuBuilder builder, Session session) {
		ItemBuilder button = new ItemBuilder(Material.EMERALD)
				.setDisplayName(ChatUtil.color("&aClick to open villager merchant"));

		builder.slot(OPEN_SLOT, ItemComponent.of(button).onAnyClick(ctx -> openAction.accept(ctx.player())));

		Material fillMaterial = XMaterial.matchXMaterial(InventoryBuilder.DEFAULT_FILL_ITEM)
		                                 .map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
		builder.fill(FillComponent.of(fillMaterial).name(InventoryBuilder.DEFAULT_FILL_NAME));
	}

	public static final class Session implements FlowState { }
}
