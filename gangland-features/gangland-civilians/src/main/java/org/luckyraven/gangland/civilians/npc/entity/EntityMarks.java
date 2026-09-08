package org.luckyraven.gangland.civilians.npc.entity;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

/**
 * Bridges Keystone's string-typed {@link NpcMarkManager} back onto the Gangland {@link EntityMark} policy the
 * manager itself refuses to know about. {@link #of} maps {@code null} (no PDC value, no configured default) to
 * {@link EntityMark#UNSET} — never a guess (P1 Q-K4).
 */
public final class EntityMarks {

	private EntityMarks() {
	}

	public static EntityMark of(@Nullable String mark) {
		if (mark == null) return EntityMark.UNSET;

		try {
			return EntityMark.valueOf(mark);
		} catch (IllegalArgumentException ignored) {
			return EntityMark.UNSET;
		}
	}

	public static boolean isCivilian(Entity entity, NpcMarkManager markManager) {
		return of(markManager.getMark(entity)).isCivilian();
	}

	public static boolean countsForWanted(Entity entity, NpcMarkManager markManager) {
		return of(markManager.getMark(entity)).countForWanted();
	}

}
