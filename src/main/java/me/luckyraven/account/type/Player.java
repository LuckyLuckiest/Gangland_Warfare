package me.luckyraven.account.type;

import me.luckyraven.data.user.User;
import me.luckyraven.account.Account;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.UUID;

public class Player extends Account<UUID, User<?>> {

	public Player(UUID uuid, User<?> user) {
		super(uuid, user);
	}

	public org.bukkit.entity.Player getBukkitPlayer() {
		return Bukkit.getOfflinePlayer(getKey()).getPlayer();
	}

	public void sendMessage(String message) {
		getBukkitPlayer().sendMessage(ChatUtil.color(message));
	}

	public void sendMessage(String message, HashMap<String, String> parameters) {
		for (String parameter : parameters.keySet())
			message = message.replaceAll(parameter, parameters.get(parameter));
		getBukkitPlayer().sendMessage(ChatUtil.color(message));
	}

}
