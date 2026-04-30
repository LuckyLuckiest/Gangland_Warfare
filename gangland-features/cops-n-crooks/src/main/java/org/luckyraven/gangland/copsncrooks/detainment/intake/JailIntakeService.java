package org.luckyraven.gangland.copsncrooks.detainment.intake;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventoryService;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.PaperworkItemFactory;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.gangland.copsncrooks.detainment.transit.TransitService;
import org.luckyraven.gangland.copsncrooks.detainment.wanted.WantedClearContract;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;

/**
 * Orchestrates the transition from HANDCUFFED → JAILED: picks a jail, seizes the inventory, zeros wanted, gives
 * paperwork, teleports the player, and writes the sentence expiry. Called when {@link TransitService} commits (either
 * the timer fired or the player died).
 */
@CustomLog
@RequiredArgsConstructor
public class JailIntakeService {

	private final DetainmentService       detainmentService;
	private final DetainmentRegistry      detainmentRegistry;
	private final JailService             jailService;
	private final JailRegistry            jailRegistry;
	private final SeizedInventoryService  seizedInventoryService;
	private final WantedClearContract     wantedClearContract;
	private final PaperworkItemFactory    paperworkItemFactory;
	private final DetainmentCostsContract costs;
	private final DetainmentSoundContract sounds;

	public boolean admit(Player player) {
		if (player == null || !player.isOnline()) return false;

		Jail jail = pickJail(player);
		if (jail == null) {
			log.warn("No non-full jail available to admit {} — releasing.", player.getName());
			detainmentService.release(player);
			return false;
		}

		int wantedLevel = wantedClearContract.getWantedLevel(player.getUniqueId());
		seizedInventoryService.snapshot(player);
		clearInventory(player);
		giveItem(player, paperworkItemFactory.create());
		wantedClearContract.clearWanted(player.getUniqueId());

		jailService.detainPlayer(jail.getId(), player.getUniqueId());
		detainmentService.jail(player, jail.getId());

		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(player.getUniqueId());
		if (detained != null) {
			detained.setTransitExpiresAt(null);
			detained.setWantedAtArrest(wantedLevel);
			long sentenceExpiresAt = System.currentTimeMillis() + costs.computeSentenceSeconds(wantedLevel) * 1000L;
			detained.setSentenceExpiresAt(sentenceExpiresAt);
			detainmentRegistry.save(detained);
		}

		sounds.playTransitCommit(player);

		return true;
	}

	private Jail pickJail(Player player) {
		Jail    best         = null;
		double  bestDistance = Double.MAX_VALUE;
		boolean sameWorld    = false;

		for (Jail jail : jailRegistry.getCells()) {
			if (jail.getJailedPlayersId().size() >= jail.getMaxCapacity()) continue;

			boolean isSameWorld = jail.getLocation().getWorld() != null &&
			                      jail.getLocation().getWorld().equals(player.getWorld());

			if (best == null) {
				best         = jail;
				bestDistance = isSameWorld ?
				               player.getLocation().distanceSquared(jail.getLocation()) :
				               Double.MAX_VALUE;
				sameWorld    = isSameWorld;
				continue;
			}

			// Prefer same-world jails; within same-world, pick the closest.
			if (isSameWorld && !sameWorld) {
				best         = jail;
				bestDistance = player.getLocation().distanceSquared(jail.getLocation());
				sameWorld    = true;
				continue;
			}

			if (isSameWorld && sameWorld) {
				double distance = player.getLocation().distanceSquared(jail.getLocation());
				if (distance < bestDistance) {
					best         = jail;
					bestDistance = distance;
				}
			}
		}

		return best;
	}

	private void clearInventory(Player player) {
		PlayerInventory inventory = player.getInventory();
		inventory.clear();
		inventory.setHelmet(null);
		inventory.setChestplate(null);
		inventory.setLeggings(null);
		inventory.setBoots(null);
		inventory.setItemInOffHand(null);
	}

	private void giveItem(Player player, ItemStack item) {
		if (item == null) return;
		player.getInventory().setItem(0, item);
	}
}
