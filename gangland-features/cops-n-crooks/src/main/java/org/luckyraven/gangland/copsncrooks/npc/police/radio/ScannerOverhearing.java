package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerSettings;
import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSupport;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Who overhears a squad's radio lines beyond the ordinary radio range: a player carrying the Police Scanner anywhere in
 * the inventory (or off hand) while some squad member is within {@code Cops.Scanner.Range}. Installed on
 * {@link CopRadio#setOverhearing}. @since 0.20.0
 */
public class ScannerOverhearing implements BiPredicate<Player, NpcSquad> {

	private final Supplier<ScannerSettings> settings;
	private final Predicate<ItemStack>      isScanner;
	private final Predicate<Player>         isNpc;

	public ScannerOverhearing(Supplier<ScannerSettings> settings, Predicate<ItemStack> isScanner) {
		this(settings, isScanner, NpcSupport::isNpc);
	}

	public ScannerOverhearing(Supplier<ScannerSettings> settings, Predicate<ItemStack> isScanner,
	                          Predicate<Player> isNpc) {
		this.settings  = settings;
		this.isScanner = isScanner;
		this.isNpc     = isNpc;
	}

	@Override
	public boolean test(Player player, NpcSquad squad) {
		if (isNpc.test(player)) {
			return false;
		}

		ScannerSettings current = settings.get();
		if (!current.enabled() || !holdsScanner(player)) {
			return false;
		}

		World world = player.getWorld();
		Location location = player.getLocation();
		if (world == null || location == null) {
			return false;
		}

		for (AbstractNpc member : squad.members()) {
			LivingEntity entity = member.getEntity();
			if (entity == null || entity.getWorld() != world) {
				continue;
			}
			if (entity.getLocation().distance(location) <= current.range()) {
				return true;
			}
		}
		return false;
	}

	private boolean holdsScanner(Player player) {
		PlayerInventory inventory = player.getInventory();
		if (inventory == null) {
			return false;
		}

		ItemStack[] contents = inventory.getContents();
		if (contents != null) {
			for (ItemStack stack : contents) {
				if (stack != null && isScanner.test(stack)) {
					return true;
				}
			}
		}

		ItemStack offHand = inventory.getItemInOffHand();
		return offHand != null && isScanner.test(offHand);
	}
}
