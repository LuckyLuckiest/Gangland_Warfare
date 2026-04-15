package me.luckyraven.shop.event;

import lombok.Getter;
import me.luckyraven.shop.ShopDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after an admin finishes editing a shop via {@code ShopAdminView}. Any shop integration (trader NPC, vending
 * kiosk, black-market terminal) shares the same edit flow and persistence concerns, so this event lives in shop-api
 * rather than beside a specific feature module.
 */
@Getter
public class ShopEditedEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player         admin;
	private final ShopDefinition definition;

	public ShopEditedEvent(Player admin, ShopDefinition definition) {
		this.admin      = admin;
		this.definition = definition;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
