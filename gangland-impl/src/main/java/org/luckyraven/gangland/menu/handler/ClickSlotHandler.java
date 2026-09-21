package org.luckyraven.gangland.menu.handler;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.inventory.click.ClickContext;
import org.luckyraven.keystone.inventory.click.ClickHandler;
import org.luckyraven.keystone.inventory.registry.MenuOpener;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.menu.part.Slot;

import java.util.Collections;
import java.util.List;

/**
 * Handles {@code OnClick} and {@code OnInteract} slot events.
 *
 * <p>Also handles the right-click-only case (when {@link SlotContext#eventSection()} is
 * {@code null} but {@link SlotContext#rightClickSection()} is present).
 */
public class ClickSlotHandler implements SlotEventHandler {

	private final JavaPlugin plugin;

	public ClickSlotHandler(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	private static String stripSlash(String command) {
		return command.startsWith("/") ? command.substring(1) : command;
	}

	@Override
	public Slot handle(SlotContext ctx, MenuOpener opener) {
		ItemBuilder item = SlotItemFactory.create(ctx.itemResolver(), ctx.item(), ctx.itemName(), ctx.data(),
		                                          ctx.lore(), ctx.enchanted());

		// Right-click-only: no left-click event section was found in the YAML
		if (ctx.eventSection() == null) {
			return buildRightClickOnly(ctx, item, opener);
		}

		Slot slot = new Slot(ctx.slotLoc(), true, ctx.draggable(), item);

		applyLeftClick(slot, ctx.eventSection(), opener);

		if (ctx.rightClickSection() != null) {
			applyRightClick(slot, ctx.rightClickSection(), opener);
		}

		return slot;
	}

	private Slot buildRightClickOnly(SlotContext ctx, ItemBuilder item, MenuOpener opener) {
		Slot slot = new Slot(ctx.slotLoc(), true, ctx.draggable(), item);

		// Empty left-click so the slot is still registered as clickable
		slot.setClickable(clickCtx -> { });

		if (ctx.rightClickSection() != null) {
			applyRightClick(slot, ctx.rightClickSection(), opener);
		}

		return slot;
	}

	private void applyLeftClick(Slot slot, ConfigurationSection section, MenuOpener opener) {
		slot.setClickable(buildAction(section, opener));
	}

	private void applyRightClick(Slot slot, ConfigurationSection section, MenuOpener opener) {
		slot.setRightClickable(buildAction(section, opener));
	}

	private ClickHandler buildAction(ConfigurationSection section, MenuOpener opener) {
		String command    = section.getString("Command");
		String permission = section.getString("Permission");

		String inventoryName = section.isString("Inventory") ? section.getString("Inventory") : null;
		ConfigurationSection inventorySection =
				section.isConfigurationSection("Inventory") ? section.getConfigurationSection("Inventory") : null;

		AnvilSpec anvilSpec = null;
		if (inventorySection != null && "anvil".equalsIgnoreCase(inventorySection.getString("Type"))) {
			String successCommand = null;
			var    successSection = inventorySection.getConfigurationSection("Success");
			if (successSection != null) successCommand = successSection.getString("Command");
			anvilSpec = new AnvilSpec(inventorySection.getString("Title", "Enter Text"),
			                          inventorySection.getString("Text", ""), successCommand);
		}

		final AnvilSpec finalAnvilSpec = anvilSpec;

		return (ClickContext ctx) -> {
			Player player = ctx.player();
			if (permission != null && !player.hasPermission(permission)) return;
			if (command != null) player.performCommand(stripSlash(command));
			if (inventoryName != null) opener.open(player, inventoryName);
			if (finalAnvilSpec != null) openAnvil(player, finalAnvilSpec);
		};
	}

	private void openAnvil(Player player, AnvilSpec spec) {
		new AnvilGUI.Builder().onClick((slotId, snapshot) -> {
			if (slotId != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

			String output = snapshot.getText();

			if (spec.successCommand() != null) {
				String command = spec.successCommand().replace("%gangland_anvil_output%", output);
				snapshot.getPlayer().performCommand(stripSlash(command));
			}

			return List.of(AnvilGUI.ResponseAction.close());
		}).text(spec.text()).title(spec.title()).plugin(plugin).open(player);
	}

	private record AnvilSpec(String title, String text, String successCommand) { }
}
