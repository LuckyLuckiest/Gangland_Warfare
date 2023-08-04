package me.luckyraven.bukkit.inventory.sign;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
		Player player = event.getPlayer();

		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if (!(Objects.requireNonNull(event.getClickedBlock()).getState() instanceof Sign sign)) return;
		if (!signMap.containsKey(player)) return;

		SignInputAction inputAction = signMap.get(player);
		inputAction.onSignInput(player, sign.getLines());

		event.setCancelled(true);
	}

	@EventHandler
	public void onPlayerSignExit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		if (!signMap.containsKey(player)) return;

		signMap.remove(player);
	}

}
