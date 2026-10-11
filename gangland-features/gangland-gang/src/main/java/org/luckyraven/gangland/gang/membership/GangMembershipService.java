package org.luckyraven.gangland.gang.membership;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.events.gangs.GangMemberLeftEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Every way a player enters or leaves a gang. Seam: the bodies belong to lane W5-M; until then each call fails with
 * {@code not_yet} and the commands keep their own logic.
 */
public class GangMembershipService {

	public enum JoinSource {
		CREATE,
		INVITE,
		JOIN,
		STAFF
	}

	public record Result(boolean ok, String messageKey, Map<String, String> placeholders) {

		/** The success result (named {@code success} because a record accessor already is {@code ok()}). */
		public static Result success() {
			return new Result(true, "", Map.of());
		}

		public static Result fail(String messageKey) {
			return new Result(false, messageKey, Map.of());
		}
	}

	public Result join(int gangId, UUID player, JoinSource source) {
		return Result.fail("not_yet");
	}

	public Result leave(UUID player, GangMemberLeftEvent.Reason reason) {
		return Result.fail("not_yet");
	}

	public Result kick(int gangId, UUID actor, UUID target) {
		return Result.fail("not_yet");
	}

	public Result disband(int gangId, @Nullable UUID actor, boolean staff) {
		return Result.fail("not_yet");
	}

	public Result setLeader(int gangId, @Nullable UUID actor, UUID target, boolean staff) {
		return Result.fail("not_yet");
	}
}
