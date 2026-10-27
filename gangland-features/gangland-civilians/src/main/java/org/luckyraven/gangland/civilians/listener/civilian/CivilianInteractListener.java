package org.luckyraven.gangland.civilians.listener.civilian;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * The criminal right-clicks the civilian calling 911 about him: within {@code Witness.Intimidate_Range} he takes the
 * phone and the call stops (0.21.1). Right-clicking anyone else does nothing.
 *
 * <p>{@code condition = "isCitizensAvailable"}: the handler's parameter is Citizens' {@link NPCRightClickEvent}, so
 * scanning this class on a Citizens-less server would throw {@code NoClassDefFoundError}.
 *
 * @since 0.21.1
 */
@ListenerHandler(condition = "isCitizensAvailable")
public class CivilianInteractListener implements Listener {

	private final CivilianService civilians;
	private final WitnessService  witnesses;

	public CivilianInteractListener(CivilianService civilians, WitnessService witnesses) {
		this.civilians = civilians;
		this.witnesses = witnesses;
	}

	@EventHandler
	public void onNpcRightClick(NPCRightClickEvent event) {
		Entity entity = event.getNPC().getEntity();
		if (entity == null) return;

		CivilianNpc npc = civilians.getNpc(entity.getUniqueId());
		if (npc == null || npc.getWitnessCall() == null) return;

		witnesses.intimidate(npc, event.getClicker());
	}
}
