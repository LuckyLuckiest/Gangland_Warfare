package org.luckyraven.gangland.gang.membership;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.membership.GangMembershipService.Result;

import java.util.UUID;

/**
 * Hands a gang to another member. Seam: lane W5-M extracts the body from {@code GangTransferCommand}. {@code staff}
 * skips the caller-is-owner check and the confirm lock.
 */
public class GangLeadership {

	public Result transfer(Gang gang, @Nullable UUID actor, UUID target, boolean staff) {
		return Result.fail("not_yet");
	}
}
