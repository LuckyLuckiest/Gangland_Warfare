package me.luckyraven.bukkit.inventory.sign;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

public class SignInputHandler implements Listener {

	private final Map<Player, SignInputAction> signMap;

	public SignInputHandler() {
		signMap = new HashMap<>();
	}

	public void open(Player player, String[] message, SignInputAction signInputAction) {
		if (message.length != 4) throw new IllegalArgumentException("The message array must have 4 lines.");

		signMap.put(player, signInputAction);
		player.sendSignChange(player.getLocation(), message);
	}

	@EventHandler
	public void onPlayerSignInteract(PlayerInteractEvent event) {

	}

	@EventHandler
	public void onPlayerSignExit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		if (!signMap.containsKey(player)) return;

		SignInputAction input = signMap.get(player);
	}

}
