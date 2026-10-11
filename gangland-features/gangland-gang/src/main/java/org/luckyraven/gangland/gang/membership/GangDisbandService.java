package org.luckyraven.gangland.gang.membership;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.membership.GangMembershipService.Result;

import java.util.UUID;

/** Disbands a gang. Seam: lane W5-M extracts the body from {@code GangDeleteCommand}. */
public class GangDisbandService {

	public Result disband(Gang gang, @Nullable UUID actor, boolean staff, boolean refundFee) {
		return Result.fail("not_yet");
	}
}
