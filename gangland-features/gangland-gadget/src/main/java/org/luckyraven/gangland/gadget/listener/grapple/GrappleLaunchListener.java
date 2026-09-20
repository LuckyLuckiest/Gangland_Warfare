package org.luckyraven.gangland.gadget.listener.grapple;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.gadget.grapple.Grapple;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.gadget.grapple.message.GrappleMessages;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Launches a grapple pull off the vanilla fishing-rod hook mechanic: a grapple item is a fishing rod under the
 * hood, so a landed hook ({@link PlayerFishEvent.State#IN_GROUND}) starts the pull toward the hook's location.
 * Never reads {@code event.getHand()} — that accessor does not exist on this plugin's Spigot API floor
 * (1.16.5-1.18.2); the held item is always looked up via {@code getItemInMainHand()} instead, matching
 * {@code CarInteractListener}'s convention of ignoring off-hand entirely.
 */
@ListenerHandler
@AutowireTarget({GrappleService.class, GrappleAddon.class, GrappleMessages.class})
public class GrappleLaunchListener implements Listener {

	private final GrappleService  grappleService;
	private final GrappleAddon    grappleAddon;
	private final GrappleMessages grappleMessages;

	public GrappleLaunchListener(GrappleService grappleService, GrappleAddon grappleAddon,
	                             GrappleMessages grappleMessages) {
		this.grappleService  = grappleService;
		this.grappleAddon    = grappleAddon;
		this.grappleMessages = grappleMessages;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerFish(PlayerFishEvent event) {
		Player    player = event.getPlayer();
		ItemStack item    = player.getInventory().getItemInMainHand();

		String id = Grapple.getGrappleId(item);
		if (id == null) return;   // not a grapple item — leave vanilla fishing untouched

		Grapple grapple = grappleAddon.getGrapple(id);
		if (grapple == null) return;

		switch (event.getState()) {
			case IN_GROUND -> handleLanded(event, player, grapple);
			case CAUGHT_FISH, CAUGHT_ENTITY -> event.setCancelled(true);   // never actually "fish" with a grapple
			default -> {
				// FISHING, FAILED_ATTEMPT, REEL_IN, BITE — nothing to do, let vanilla hook physics play out
			}
		}
	}

	private void handleLanded(PlayerFishEvent event, Player player, Grapple grapple) {
		if (!player.hasPermission(grapple.getPermission())) {
			player.sendMessage(grappleMessages.noPermission());
			return;
		}

		Location anchor = event.getHook().getLocation();
		// G4: Grapple_Blocked is sent for range/border/LOS refusals — the player did something wrong (aimed too
		// far, at a wall, out of bounds) and needs feedback. It is deliberately NOT sent for start()'s own
		// cooldown/already-active no-op below — the player already knows about those (they just used it, or are
		// mid-pull), so a second message would be noise, not feedback.
		if (!isWithinMaxDistance(player, grapple, anchor)) {
			player.sendMessage(grappleMessages.blocked());
			return;
		}
		if (!isWithinWorldBorder(anchor)) {
			player.sendMessage(grappleMessages.blocked());
			return;
		}
		if (grapple.isRequireLineOfSight() && !hasLineOfSight(player, anchor)) {
			player.sendMessage(grappleMessages.blocked());
			return;
		}

		grappleService.start(player, grapple, anchor);
	}

	/**
	 * Fix round 1 (F1 Critical): {@code Max_Distance} was parsed by {@code GrappleAddon} and carried on {@link
	 * Grapple} but never read anywhere — the anchor range was unbounded. Same-world (a hook can only ever land in
	 * the player's own world, but guard it explicitly rather than assume) + squared-distance compare, avoiding a
	 * sqrt.
	 */
	private boolean isWithinMaxDistance(Player player, Grapple grapple, Location anchor) {
		World anchorWorld = anchor.getWorld();
		if (anchorWorld == null || !anchorWorld.equals(player.getWorld())) return false;

		double maxDistance = grapple.getMaxDistance();
		return player.getLocation().distanceSquared(anchor) <= maxDistance * maxDistance;
	}

	private boolean isWithinWorldBorder(Location anchor) {
		World world = anchor.getWorld();
		if (world == null) return false;
		return world.getWorldBorder().isInside(anchor);
	}

	/**
	 * Traces from the player's eyes toward the anchor, stopping 1 block short of it so the anchor block itself
	 * (the block the hook is stuck in — always a "hit" if traced all the way) never counts as an occlusion; only a
	 * block strictly between the player and the anchor refuses the launch.
	 */
	private boolean hasLineOfSight(Player player, Location anchor) {
		Location eye   = player.getEyeLocation();
		World    world = eye.getWorld();
		if (world == null) return false;

		Vector toAnchor = anchor.toVector().subtract(eye.toVector());
		double distance = toAnchor.length();
		if (distance <= 1.0) return true;   // adjacent to the anchor — nothing meaningful to occlude

		RayTraceResult result = world.rayTraceBlocks(eye, toAnchor.normalize(), distance - 1.0);
		return result == null;
	}
}
