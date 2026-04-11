package me.luckyraven.copsncrooks.npc.police;

import lombok.Getter;
import me.luckyraven.copsncrooks.npc.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpc;
import me.luckyraven.copsncrooks.npc.police.state.CopBehaviorFactory;
import me.luckyraven.copsncrooks.npc.police.state.CuffLockRegistry;

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
 */
@Getter
public class CopGroup {

	private final UUID         targetPlayerId;
	private final List<CopNpc> cops;

	public CopGroup(UUID targetPlayerId) {
		this.targetPlayerId = targetPlayerId;
		this.cops           = Collections.synchronizedList(new ArrayList<>());
	}

	public void add(CopNpc cop) {
		cops.add(cop);
	}

	public boolean isEmpty() {
		return cops.isEmpty();
	}

	public void destroyAll(EntityMarkManager entityMarkManager) {
		for (CopNpc cop : cops) {
			cop.destroy(entityMarkManager);
		}
		cops.clear();
	}
}