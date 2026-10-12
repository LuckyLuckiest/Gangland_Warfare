package org.luckyraven.gangland.turf.npc.guard;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

public final class TurfOwnerAlerts {

	public TurfOwnerAlerts(TurfManager turfs, GangLookupContract gangs, TurfMessages text, IntSupplier cooldownSeconds) {
	}

	void setClock(LongSupplier clock) {
	}

	void setSender(BiConsumer<UUID, String> sender) {
	}

	public void copHit(Turf turf, Player victim) {
	}

	public void guardDown(int turfId, Object npc) {
	}

	public void quartermasterHit(int turfId, String attacker) {
	}

	public void quartermasterDown(int turfId, Object npc) {
	}
}
