package me.luckyraven.listener.player;

import me.luckyraven.data.user.User;
import me.luckyraven.account.type.Gang;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;


public final class CreateAccount implements Listener {

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<UUID>   user   = new User<>(player.getUniqueId());
		Set<User<?>> group  = new LinkedHashSet<>();
		group.add(user);
		Gang gang = new Gang(123, group, "Dragon");
		gang.getGroup().add(user);
		gang.getGroup().add(user);
		gang.getGroup().add(user);
		player.sendMessage(gang.toString());
	}

}
