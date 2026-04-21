package me.luckyraven.copsncrooks.listener.police;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.detainment.paperwork.HandcuffBribeView;
import me.luckyraven.copsncrooks.npc.police.CopManager;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpc;
import me.luckyraven.copsncrooks.npc.police.state.CuffLockRegistry;
import me.luckyraven.core.listener.ListenerHandler;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * When a HANDCUFFED player right-clicks the specific cop NPC that is guarding them, opens the handcuff bribe GUI so
 * they can pay to walk free. Ignores right-clicks on other cops so a crook can't bribe random passers-by — the GUI is
 * scoped to the cop holding the cuff lock.
 */
@ListenerHandler
@RequiredArgsConstructor
public class HandcuffBribeListener implements Listener {

	private final DetainmentService detainmentService;
	private final CopManager        copManager;
	private final CuffLockRegistry  cuffLockRegistry;
	private final HandcuffBribeView handcuffBribeView;

	@EventHandler
	public void onNpcRightClick(NPCRightClickEvent event) {
		Player player = event.getClicker();
		if (!detainmentService.isHandcuffed(player)) return;

		Entity entity = event.getNPC().getEntity();
		if (entity == null) return;

		CopNpc cop = copManager.findCopByEntity(entity);
		if (cop == null) return;

		if (!cuffLockRegistry.isOwner(player.getUniqueId(), cop.getNpc().getUniqueId())) return;

		handcuffBribeView.open(player, cop);
	}
}
