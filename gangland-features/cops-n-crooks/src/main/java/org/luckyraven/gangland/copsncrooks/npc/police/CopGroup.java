package org.luckyraven.gangland.copsncrooks.npc.police;

import lombok.Getter;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A group of cop NPCs all pursuing the same target player.
 * <p>
 * Provides lifecycle management for the cop list. Cuff-lock coordination is intentionally NOT scoped per-group: cops
 * may retarget to a different player after their original target loses wanted status, and a per-group registry would
 * allow two groups to simultaneously hold the lock on the same target. The single global {@link CuffLockRegistry} in
 * {@link CopBehaviorFactory} is keyed by target UUID and correctly serializes cuffing attempts across all groups.
 * <p>
 * The group owns one Keystone {@link NpcSquad}: the cops' shared awareness of the target (last-known position,
 * sighting time, routes). A cop joins it when {@link #add(CopNpc) assigned} and leaves it when
 * {@link #release(CopNpc, NpcMarkManager) released} (despawned, killed or invalid) or when it starts returning
 * ({@link CopNpc#leaveSquad()}).
 */
@Getter
public class CopGroup {

	private final UUID         targetPlayerId;
	private final List<CopNpc> cops;
	private final NpcSquad     squad;

	public CopGroup(UUID targetPlayerId) {
		this.targetPlayerId = targetPlayerId;
		this.cops           = Collections.synchronizedList(new ArrayList<>());
		this.squad          = new NpcSquad();
	}

	public void add(CopNpc cop) {
		cops.add(cop);
		squad.add(cop);
		cop.setGroup(this);
	}

	/**
	 * Destroys a cop that left the group (despawned, killed or invalid) and drops it from the squad. The caller removes
	 * it from {@link #getCops()}: both call sites are iterating that list.
	 */
	public void release(CopNpc cop, NpcMarkManager markManager) {
		cop.destroy(entity -> markManager.removeMark(entity));
		squad.remove(cop);
	}

	public boolean isEmpty() {
		return cops.isEmpty();
	}

	public void destroyAll(NpcMarkManager markManager) {
		for (CopNpc cop : cops) {
			cop.destroy(entity -> markManager.removeMark(entity));
		}
		cops.clear();
	}
}
