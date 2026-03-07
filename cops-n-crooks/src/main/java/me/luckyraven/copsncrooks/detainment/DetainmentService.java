package me.luckyraven.copsncrooks.detainment;

import lombok.Getter;
import me.luckyraven.copsncrooks.detainment.jail.JailService;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

@Getter
public class DetainmentService {

	// TODO change this instance
	public static final String COMMAND_BYPASS_PERMISSION = "gangland.detainment.bypass.command";

	private final JavaPlugin           plugin;
	private final DetainmentRepository repository;
	private final JailService          jailService;

	public DetainmentService(JavaPlugin plugin, DetainmentRepository repository, JailService jailService) {
		this.plugin      = plugin;
		this.repository  = repository;
		this.jailService = jailService;
	}

	public DetainmentState getState(Player player) {
		return getState(player.getUniqueId());
	}

	public DetainmentState getState(UUID playerId) {
		return repository.loadState(playerId);
	}

	public boolean isHandcuffed(Player player) {
		return getState(player) == DetainmentState.HANDCUFFED;
	}

	public boolean isJailed(Player player) {
		return getState(player) == DetainmentState.JAILED;
	}

	public boolean isRestrained(Player player) {
		DetainmentState state = getState(player);
		return state == DetainmentState.HANDCUFFED || state == DetainmentState.JAILED;
	}

	public void handcuff(Player player) {
		setState(player, DetainmentState.HANDCUFFED);
		applyVisuals(player, true);
		ChatUtil.sendTitle(player, "&cHandcuffed", "&7You are restrained");
		ChatUtil.sendActionBar(plugin, player, "&cYou are handcuffed", 40L);
	}

	public void jail(Player player) {
		setState(player, DetainmentState.JAILED);
		applyVisuals(player, true);
		teleportToJail(player);
		ChatUtil.sendTitle(player, "&4Jailed", "&7You have been transported to jail");
		ChatUtil.sendActionBar(plugin, player, "&4You are jailed", 40L);
	}

	public void release(Player player) {
		setState(player, DetainmentState.NORMAL);
		clearVisuals(player);
		ChatUtil.sendTitle(player, "&aReleased", "&7You are no longer restrained");
	}

	public void setState(Player player, DetainmentState state) {
		repository.saveState(player.getUniqueId(), state);

		if (state == DetainmentState.NORMAL) {
			clearVisuals(player);
			return;
		}

		applyVisuals(player, false);
	}

	public void handleJoin(Player player) {
		DetainmentState state = getState(player);

		if (state == DetainmentState.NORMAL) {
			clearVisuals(player);
			return;
		}

		applyVisuals(player, true);

		if (state == DetainmentState.JAILED) {
			Bukkit.getScheduler().runTask(plugin, () -> teleportToJail(player));
		}
	}

	public void handleQuit(Player player) {
		if (getState(player) != DetainmentState.HANDCUFFED) return;

		repository.saveState(player.getUniqueId(), DetainmentState.JAILED);
	}

	public void handleRespawn(Player player) {
		if (!isJailed(player)) return;

		Bukkit.getScheduler().runTask(plugin, () -> {
			applyVisuals(player, false);
			teleportToJail(player);
		});
	}

	public void tickVisuals(Player player) {
		DetainmentState state = getState(player);

		if (state == DetainmentState.NORMAL) return;
		if (!player.isOnline()) return;
		if (player.isDead()) return;
		if (player.getGameMode() == GameMode.SPECTATOR) return;

		applyVisuals(player, false);

		if (state == DetainmentState.HANDCUFFED) {
			ChatUtil.sendActionBar(plugin, player, "&cHandcuffed &8- &7You cannot interact", 25L);
			return;
		}

		ChatUtil.sendActionBar(plugin, player, "&4Jailed &8- &7You cannot interact", 25L);
	}

	private void teleportToJail(Player player) {
		Location jailLocation = jailService.getJailLocation(player.getUniqueId());
		if (jailLocation == null) return;

		player.teleport(jailLocation);
	}

	private void applyVisuals(Player player, boolean forceInventoryClose) {
		if (forceInventoryClose) player.closeInventory();

		player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 4, false, false, false));
		player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
	}

	private void clearVisuals(Player player) {
		player.removePotionEffect(PotionEffectType.SLOWNESS);
		player.removePotionEffect(PotionEffectType.BLINDNESS);
	}
}
