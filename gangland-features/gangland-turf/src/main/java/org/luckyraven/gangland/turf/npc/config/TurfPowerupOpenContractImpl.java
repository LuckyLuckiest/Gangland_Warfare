package org.luckyraven.gangland.turf.npc.config;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupNpc;
import org.luckyraven.gangland.turf.npc.TurfPowerupOpenContract;
import org.luckyraven.gangland.turf.npc.view.TurfPowerupFlow;

import java.util.function.Supplier;

/**
 * gangland-impl impl of the cops-n-crooks {@link TurfPowerupOpenContract}. Resolves the turf, the viewer's gang, and
 * the owning gang; gates with the same checks the panel would otherwise need to know about (turf still exists, turf is
 * owned, viewer has a gang, viewer's gang IS the owner or an ally). Failures send a chat deny and never construct the
 * inventory flow. Deny lines and the fallback Quartermaster name come from the turf module's YAML (W2-Q, TF-38).
 */
@RequiredArgsConstructor
public final class TurfPowerupOpenContractImpl implements TurfPowerupOpenContract {

	private final TurfPowerupFlow             flow;
	private final TurfManager                 turfs;
	private final TurfPowerupManager          npcs;
	private final GangLookupContract          gangs;
	private final UserLookupContract          users;
	private final TurfMessages                messages;
	private final Supplier<TurfPowerupSettings> settings;

	@Override
	public void open(Player viewer, int turfId) {
		Turf turf = turfs.get(turfId);
		if (turf == null) {
			viewer.sendMessage(messages.text("Quartermaster.Turf_Gone", "&cThis turf no longer exists."));
			return;
		}
		Integer ownerId = turf.getOwnerGangId();
		if (ownerId == null) {
			viewer.sendMessage(messages.text("Quartermaster.No_Owner",
			                                 "&cThis turf has no owner — nothing to manage here yet."));
			return;
		}
		User<Player> user = users.findByPlayer(viewer);
		if (user == null || !user.hasGang()) {
			viewer.sendMessage(onlyOwnerOrAllies());
			return;
		}
		Gang ownerGang  = gangs.findById(ownerId);
		Gang viewerGang = gangs.findById(user.getGangId());
		if (ownerGang == null || viewerGang == null) {
			viewer.sendMessage(messages.text("Quartermaster.Gang_Missing",
			                                 "&cGang record missing — contact an admin."));
			return;
		}
		// Owner OR allied gang may use the panel. Allies pay from THEIR own bank when buying buffs/garrison;
		// the buff/garrison still applies to the turf the owner-gang holds. Purchases re-check this (QuartermasterAccess).
		boolean isOwner = user.getGangId() == ownerId;
		boolean isAlly  = !isOwner && viewerGang.isAlly(ownerGang);
		if (!isOwner && !isAlly) {
			viewer.sendMessage(onlyOwnerOrAllies());
			return;
		}

		TurfPowerupNpc qm = npcs.get(turfId);
		String name = qm != null && qm.getData().getDisplayName() != null
		              ? qm.getData().getDisplayName()
		              : settings.get().displayName();

		flow.start(viewer, turf, ownerGang, viewerGang, name);
	}

	private String onlyOwnerOrAllies() {
		return messages.text("Quartermaster.Only_Owner_Or_Allies",
		                     "&cOnly members of the owning gang or its allies can use this NPC.");
	}
}
