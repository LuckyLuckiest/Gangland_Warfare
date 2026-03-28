package me.luckyraven.weapon.events.reload;

import lombok.Getter;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.events.WeaponEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class WeaponReloadStartEvent extends WeaponEvent {

	private static final HandlerList handler = new HandlerList();

	private final Player player;

	public WeaponReloadStartEvent(Weapon weapon, Player player) {
		super(weapon);

		this.player = player;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handler;
	}

}
