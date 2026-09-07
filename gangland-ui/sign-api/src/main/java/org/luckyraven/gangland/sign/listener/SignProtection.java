package org.luckyraven.gangland.sign.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.sign.SignPermissions;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.sign.service.SignInteractionService;

/**
 * Stops a plugin sign from being broken by a player who may not manage signs.
 *
 * <p>Before docket LS-19 there was no {@code BlockBreakEvent} handler at all: any player who could break blocks
 * could delete another player's shop sign. A sign carries no owner — the plugin stores nothing per sign beyond its
 * four lines — so the guard is the {@link SignPermissions#BREAK} node rather than an owner comparison. Sign
 * identification reuses {@code SignTypeRegistry.findByLine}, exactly as {@link PlayerSignInteract} does, so a
 * formatted sign and a hand-written one are treated the same way.
 *
 * <p>Observation #19 (lootchests-signs-waypoints.md), docket LS-19.
 */
@ListenerHandler
@RequiredArgsConstructor
public class SignProtection implements Listener {

	private final SignInteractionService signService;
	private final SignInformation        information;

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onSignBreak(BlockBreakEvent event) {
		Block block = event.getBlock();

		if (!(block.getState() instanceof Sign sign)) {
			return;
		}

		//noinspection deprecation
		String[] lines = sign.getLines();

		if (lines.length == 0 || lines[0] == null) {
			return;
		}

		if (signService.getRegistry().findByLine(lines[0]).isEmpty()) {
			return;
		}

		Player player = event.getPlayer();

		if (player.hasPermission(SignPermissions.BREAK)) {
			return;
		}

		event.setCancelled(true);
		player.sendMessage(information.getSignNoPermission());
	}

}
