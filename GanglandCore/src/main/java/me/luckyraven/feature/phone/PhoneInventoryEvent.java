package me.luckyraven.feature.phone;

import lombok.Getter;
import me.luckyraven.data.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

@Getter
public class PhoneInventoryEvent extends Event implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private final User<Player> user;
	private final Phone        phone;
	private final Inventory    inventory;

	private boolean cancelled;

	public PhoneInventoryEvent(User<Player> user) {
		this.user      = user;
		this.phone     = user.getPhone();
		this.inventory = phone.getInventoryHandler();
		this.cancelled = false;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handler;
	}

}
