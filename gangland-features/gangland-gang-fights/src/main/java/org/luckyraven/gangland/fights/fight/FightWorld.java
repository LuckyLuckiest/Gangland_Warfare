package org.luckyraven.gangland.fights.fight;

import org.luckyraven.gangland.fights.arena.ArenaPoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the fight flow needs from the server and the gang module, behind one seam so the state machine runs
 * in a plain unit test. {@code BukkitFightWorld} is the real one.
 */
public interface FightWorld {

	Optional<Integer> gangOf(UUID player);

	Optional<Integer> gangByName(String name);

	String gangName(int gangId);

	/** Names of every gang, for tab completion. */
	List<String> gangNames();

	boolean allied(int gangA, int gangB);

	/** Rank gate: {@code GangPermissions.allows(member, player, FIGHT)}. */
	boolean mayFight(UUID player);

	/** Rank gate: {@code GangPermissions.allows(member, player, WITHDRAW)}. */
	boolean mayWithdraw(UUID player);

	double bank(int gangId);

	long gangAgeDays(int gangId);

	long memberAgeHours(UUID player);

	boolean online(UUID player);

	boolean downed(UUID player);

	boolean wanted(UUID player);

	/** Online members of the gang who pass the fight rank gate: they receive challenges. */
	List<UUID> officers(int gangId);

	List<UUID> onlineMembers(int gangId);

	boolean teleport(UUID player, ArenaPoint to);

	PlayerSnapshot snapshot(UUID player);

	void restore(PlayerSnapshot snapshot);

	/** Sends the {@code fight_messages.yml} text of {@code key}; {@code pairs} are name, value, name, value... */
	void tell(UUID player, String key, Object... pairs);
}
