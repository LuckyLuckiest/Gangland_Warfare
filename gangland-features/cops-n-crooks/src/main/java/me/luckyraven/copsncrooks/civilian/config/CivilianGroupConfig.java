package me.luckyraven.copsncrooks.civilian.config;

import java.util.Map;

/**
 * Configuration for a civilian spawn-group.
 * <p>
 * A group spawns multiple NPC types together; members stay near each other and share trait bonuses.
 *
 * @param groupId unique key from entity_marker.yml
 * @param displayName color-coded group name
 * @param hostile if true all members are treated as hostile regardless of their type setting
 * @param healthBonus extra max health added to every member (half-hearts)
 * @param speedBonus speed multiplier bonus added to every member
 * @param stayTogetherRange members navigate back toward the group center when they stray beyond this (blocks)
 * @param members map of type-id → spawn count
 */
public record CivilianGroupConfig(
		String groupId,
		String displayName,
		boolean hostile,
		double healthBonus,
		double speedBonus,
		double stayTogetherRange,
		Map<String, Integer> members
) {
}
