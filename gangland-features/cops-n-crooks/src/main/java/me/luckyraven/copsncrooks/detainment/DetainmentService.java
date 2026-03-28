package me.luckyraven.copsncrooks.detainment;

import com.cryptomorin.xseries.XPotion;
import lombok.Getter;
import me.luckyraven.copsncrooks.jail.JailRegistry;
import me.luckyraven.copsncrooks.jail.JailService;
import me.luckyraven.util.downed.DownedPlayerRegistry;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.Map;
import java.util.UUID;

public class DetainmentService {

	private final JavaPlugin         plugin;
	private final DetainmentRegistry detainmentRegistry;
	private final JailService        jailService;
	private final JailRegistry       jailRegistry;

	@Getter
	private final String commandBypassPermission;

	public DetainmentService(JavaPlugin plugin, DetainmentRegistry detainmentRegistry, JailService jailService,
	                         JailRegistry jailRegistry, String prefix) {
		this.plugin                  = plugin;
		this.detainmentRegistry      = detainmentRegistry;
		this.jailService             = jailService;
		this.jailRegistry            = jailRegistry;
		this.commandBypassPermission = prefix + ".detainment.bypass.command";
	}

	public DetainmentState getState(Player player) {
		return getState(player.getUniqueId());
	}

	public DetainmentState getState(UUID playerId) {
		return detainmentRegistry.getState(playerId);
	}

	public Map<UUID, DetainmentState> getDetainedPlayers() {
		return detainmentRegistry.getStates();
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

	public void jail(Player player, int jailId) {
		jailService.detainPlayer(jailId, player.getUniqueId());
		setState(player, DetainmentState.JAILED);
		applyVisuals(player, true);
		teleportToJail(player);
		ChatUtil.sendTitle(player, "&4Jailed", "&7You have been transported to jail");
		ChatUtil.sendActionBar(plugin, player, "&4You are jailed", 40L);
	}

	public void release(Player player) {
		jailRegistry.releasePlayer(player.getUniqueId());
		setState(player, DetainmentState.NORMAL);
		clearVisuals(player);
		ChatUtil.sendTitle(player, "&aReleased", "&7You are no longer restrained");
	}

	public void setState(Player player, DetainmentState state) {
		detainmentRegistry.setState(player.getUniqueId(), state);

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

		detainmentRegistry.setState(player.getUniqueId(), DetainmentState.JAILED);
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
		if (player.isDead() || DownedPlayerRegistry.isDowned(player.getUniqueId())) return;
		if (player.getGameMode() == GameMode.SPECTATOR) return;

		applyVisuals(player, false);

		if (state == DetainmentState.HANDCUFFED) {
			ChatUtil.sendActionBar(plugin, player, "&cHandcuffed &8- &7You cannot interact", 25L);
			return;
		}

		ChatUtil.sendActionBar(plugin, player, "&4Jailed &8- &7You cannot interact", 25L);
	}

	private void teleportToJail(Player player) {
		Location jailLocation = jailRegistry.getJailLocation(player.getUniqueId());
		if (jailLocation == null) return;

		player.teleport(jailLocation);
	}

	private void applyVisuals(Player player, boolean forceInventoryClose) {
		if (forceInventoryClose) player.closeInventory();

		applyEffect(player, XPotion.SLOWNESS, 4);
		applyEffect(player, XPotion.BLINDNESS, 1);
	}

	private void clearVisuals(Player player) {
		removeEffect(player, XPotion.SLOWNESS);
		removeEffect(player, XPotion.BLINDNESS);
	}

	private void applyEffect(Player player, XPotion potion, int amplifier) {
		XPotion.of(potion.name())
		       .map(XPotion::getPotionEffectType)
		       .ifPresent(type -> player.addPotionEffect(
					   new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier)));
	}

	private void removeEffect(Player player, XPotion potion) {
		XPotion.of(potion.name()).map(XPotion::getPotionEffectType).ifPresent(player::removePotionEffect);
	}
}
