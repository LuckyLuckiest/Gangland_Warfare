package me.luckyraven.copsncrooks.detainment.release;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.combo.KillCombo;
import me.luckyraven.copsncrooks.detainment.DetainedPlayer;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.detainment.DetainmentState;
import me.luckyraven.copsncrooks.detainment.inventory.SeizedInventoryService;
import me.luckyraven.copsncrooks.detainment.paperwork.PaperworkItemFactory;
import me.luckyraven.copsncrooks.detainment.transit.TransitService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Single release point funneling every exit from detainment through the same sequence:
 * <ol>
 *   <li>Cancel any pending transit timer.</li>
 *   <li>Restore the seized inventory (no-op if none was taken).</li>
 *   <li>Strip any leftover Jail Paperwork items.</li>
 *   <li>Call {@link DetainmentService#release(Player)} to clear visuals and set state to NORMAL.</li>
 *   <li>Teleport to the jail's exit location, falling back to the configured waypoint.</li>
 * </ol>
 * All admin and gameplay release paths should go through this rather than calling {@code DetainmentService.release}
 * directly, so none forget a step.
 */
@CustomLog
public class ReleasePipeline {

	private final DetainmentService      detainmentService;
	private final DetainmentRegistry     detainmentRegistry;
	private final SeizedInventoryService seizedInventoryService;
	private final TransitService         transitService;
	private final PaperworkItemFactory   paperworkItemFactory;
	private final ReleaseExitContract    exitContract;
	private final KillCombo              killCombo;

	public ReleasePipeline(DetainmentService detainmentService, DetainmentRegistry detainmentRegistry,
	                       SeizedInventoryService seizedInventoryService, TransitService transitService,
	                       PaperworkItemFactory paperworkItemFactory, ReleaseExitContract exitContract,
	                       KillCombo killCombo) {
		this.detainmentService      = detainmentService;
		this.detainmentRegistry     = detainmentRegistry;
		this.seizedInventoryService = seizedInventoryService;
		this.transitService         = transitService;
		this.paperworkItemFactory   = paperworkItemFactory;
		this.exitContract           = exitContract;
		this.killCombo              = killCombo;
	}

	public void release(Player player, ReleaseReason reason) {
		if (player == null) return;

		DetainedPlayer detainedPlayer = detainmentRegistry.getDetainedPlayers().get(player.getUniqueId());
		Integer        jailId         = detainedPlayer == null ? null : detainedPlayer.getJailId();

		transitService.cancel(player);

		DetainmentState priorState = detainmentService.getState(player);
		if (priorState == DetainmentState.JAILED) {
			// Items are only ever seized after transit commits; only bother restoring in that case.
			seizedInventoryService.restore(player);
		} else {
			// Defensive: if someone ever seized while HANDCUFFED, still return them.
			if (seizedInventoryService.has(player.getUniqueId())) {
				seizedInventoryService.restore(player);
			}
		}

		stripPaperwork(player);

		detainmentService.release(player);

		// Clear any lingering kill-combo tracking so a freshly released player starts with a clean slate.
		killCombo.resetCombo(player.getUniqueId());

		teleportToExit(player, jailId);

		log.debug("Released {} via {}", player.getName(), reason);
	}

	private void stripPaperwork(Player player) {
		PlayerInventory inventory = player.getInventory();
		ItemStack[]     contents  = inventory.getContents();

		for (int i = 0; i < contents.length; i++) {
			ItemStack stack = contents[i];
			if (stack == null) continue;
			if (paperworkItemFactory.isPaperwork(stack)) {
				inventory.setItem(i, null);
			}
		}
	}

	private void teleportToExit(Player player, Integer jailId) {
		if (jailId == null) return;
		Location exit = exitContract.resolveExit(jailId);
		if (exit == null) return;
		player.teleport(exit);
	}
}
