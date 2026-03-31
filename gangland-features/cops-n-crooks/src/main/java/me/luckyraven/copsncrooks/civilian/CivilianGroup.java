package me.luckyraven.copsncrooks.civilian;

import lombok.Getter;
import me.luckyraven.copsncrooks.civilian.config.CivilianGroupConfig;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A spawn-group: a collection of civilian NPCs that were spawned together and are expected to stay near each other.
 */
@Getter
public class CivilianGroup {

	private final String              groupId;
	private final CivilianGroupConfig config;
	private final List<CivilianNpc>   members;

	public CivilianGroup(String groupId, CivilianGroupConfig config) {
		this.groupId = groupId;
		this.config  = config;
		this.members = new ArrayList<>();
	}

	/**
	 * Adds a member NPC to the group.
	 */
	public void addMember(CivilianNpc npc) {
		members.add(npc);
	}

	/**
	 * Removes dead or marked members from the group.
	 */
	public void pruneDeadMembers() {
		members.removeIf(npc -> !npc.isValid() || npc.isMarkedForRemoval());
	}

	/**
	 * Returns whether all members of this group have been removed.
	 */
	public boolean isEmpty() {
		return members.isEmpty();
	}

	/**
	 * Returns the average location of all alive members, or {@code null} if the group is empty.
	 */
	@Nullable
	public Location getGroupCenter() {
		if (members.isEmpty()) return null;

		double x     = 0, y = 0, z = 0;
		int    count = 0;

		for (CivilianNpc member : members) {
			if (!member.isValid()) continue;
			Location loc = member.getEntity().getLocation();
			x += loc.getX();
			y += loc.getY();
			z += loc.getZ();
			count++;
		}

		if (count == 0) return null;

		Location center = members.get(0).getEntity().getLocation().clone();
		center.setX(x / count);
		center.setY(y / count);
		center.setZ(z / count);
		return center;
	}

	/**
	 * Returns whether the given NPC has strayed beyond the configured {@code stayTogetherRange}.
	 */
	public boolean isMemberStraying(CivilianNpc npc) {
		if (!npc.isValid()) return false;
		Location center = getGroupCenter();
		if (center == null) return false;

		Location npcLoc = npc.getEntity().getLocation();
		if (center.getWorld() == null || !center.getWorld().equals(npcLoc.getWorld())) return false;

		return npcLoc.distance(center) > config.stayTogetherRange();
	}
}
