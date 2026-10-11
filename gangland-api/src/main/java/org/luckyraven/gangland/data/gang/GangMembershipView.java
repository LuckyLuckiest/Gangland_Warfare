package org.luckyraven.gangland.data.gang;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The gang module's live answer to "which gang, if any" / "are these two gangs allied" / "what's this gang
 * called" — installed onto {@link GangMembership} by the module's {@code GangMembershipInstaller} once its
 * {@code GangManager}/{@code MemberManager} beans exist. Never implemented by core; core only ever holds the
 * always-present {@link GangMembership} wrapper.
 */
public interface GangMembershipView {

	/**
	 * @return the gang id {@code uuid} currently belongs to, or {@code -1} when it belongs to none.
	 */
	int gangIdOf(UUID uuid);

	/**
	 * @return whether the two gang ids are allied. Never same-gang — callers that also want the same-gang case
	 * use {@link GangMembership#alliedOrSame(UUID, UUID)}.
	 */
	boolean gangsAllied(int gangIdA, int gangIdB);

	/**
	 * @return the gang's display name, or empty when {@code gangId} names no real gang.
	 */
	Optional<String> nameOf(int gangId);

	/**
	 * Whether {@code attacker} may not hurt {@code victim} at {@code at}. The default is the pre-2.6 rule: same gang or
	 * allied.
	 *
	 * @since gangland-api 2.6
	 */
	default boolean pvpBlocked(UUID attacker, UUID victim, @Nullable Location at) {
		int idA = gangIdOf(attacker);
		int idB = gangIdOf(victim);

		return (idA == idB && idA != -1) || gangsAllied(idA, idB);
	}

	/**
	 * @return every real gang id; empty by default.
	 *
	 * @since gangland-api 2.6
	 */
	default Set<Integer> gangIds() {
		return Collections.emptySet();
	}

	/**
	 * @return the id of the gang with this name, or {@code -1}.
	 *
	 * @since gangland-api 2.6
	 */
	default int gangIdByName(String name) {
		return -1;
	}

	/**
	 * @return the members of the gang; empty by default.
	 *
	 * @since gangland-api 2.6
	 */
	default List<UUID> membersOf(int gangId) {
		return Collections.emptyList();
	}

	/**
	 * @return the gang's owner, or empty.
	 *
	 * @since gangland-api 2.6
	 */
	default Optional<UUID> ownerOf(int gangId) {
		return Optional.empty();
	}

	/**
	 * @return the channel the player's chat goes to; {@link GangChatChannel#PUBLIC} by default.
	 *
	 * @since gangland-api 2.6
	 */
	default GangChatChannel chatChannelOf(UUID player) {
		return GangChatChannel.PUBLIC;
	}
}
