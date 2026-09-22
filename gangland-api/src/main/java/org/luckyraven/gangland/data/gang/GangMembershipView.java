package org.luckyraven.gangland.data.gang;

import java.util.Optional;
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
}
