package org.luckyraven.gangland.file.configuration.turf;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.turf.TurfPowerupManager;
import org.luckyraven.gangland.copsncrooks.npc.turf.TurfPowerupNpc;
import org.luckyraven.gangland.copsncrooks.npc.turf.TurfPowerupOpenContract;
import org.luckyraven.gangland.copsncrooks.npc.turf.view.TurfPowerupFlow;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.UserLookupContract;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

/**
 * gangland-impl impl of the cops-n-crooks {@link TurfPowerupOpenContract}. Resolves the turf, the viewer's gang, and
 * the owning gang; gates with the same checks the panel would otherwise need to know about (turf still exists, turf is
 * owned, viewer has a gang, viewer's gang IS the owner). Failures send a chat deny and never construct the inventory
 * flow.
 */
@RequiredArgsConstructor
public final class TurfPowerupOpenContractImpl implements TurfPowerupOpenContract {

	private final TurfPowerupFlow    flow;
	private final TurfManager        turfs;
	private final TurfPowerupManager npcs;
	private final GangLookupContract gangs;
	private final UserLookupContract users;

	@Override
	public void open(Player viewer, int turfId) {
		Turf turf = turfs.get(turfId);
		if (turf == null) {
			viewer.sendMessage(ChatUtil.color("&cThis turf no longer exists."));
			return;
		}
		Integer ownerId = turf.getOwnerGangId();
		if (ownerId == null) {
			viewer.sendMessage(ChatUtil.color("&cThis turf has no owner — nothing to manage here yet."));
			return;
		}
		User<Player> user = users.findByPlayer(viewer);
		if (user == null || !user.hasGang()) {
			viewer.sendMessage(ChatUtil.color("&cOnly members of the owning gang or its allies can use this NPC."));
			return;
		}
		Gang ownerGang  = gangs.findById(ownerId);
		Gang viewerGang = gangs.findById(user.getGangId());
		if (ownerGang == null || viewerGang == null) {
			viewer.sendMessage(ChatUtil.color("&cGang record missing — contact an admin."));
			return;
		}
		// Owner OR allied gang may use the panel. Allies pay from THEIR own bank when buying buffs/garrison;
		// the buff/garrison still applies to the turf the owner-gang holds.
		boolean isOwner = user.getGangId() == ownerId;
		boolean isAlly  = !isOwner && viewerGang.isAlly(ownerGang);
		if (!isOwner && !isAlly) {
			viewer.sendMessage(ChatUtil.color("&cOnly members of the owning gang or its allies can use this NPC."));
			return;
		}

		TurfPowerupNpc qm = npcs.get(turfId);
		String name = qm != null && qm.getData().getDisplayName() != null
		              ? qm.getData().getDisplayName()
		              : "Quartermaster";

		flow.start(viewer, turf, ownerGang, viewerGang, name);
	}
}
