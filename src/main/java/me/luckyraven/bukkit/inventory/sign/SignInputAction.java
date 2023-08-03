package me.luckyraven.bukkit.inventory.sign;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface SignInputAction {

	void onSignInput(Player player, String[] lines);

}
