package org.luckyraven.gangland.core.permission;

import lombok.Getter;

@Getter
public class Permission {

	private static int ID = 0;

	private final int    usedId;
	private final String permission;

	public Permission(int id, String permission) {
		this.usedId     = id;
		this.permission = permission;
	}

	public static int getNewId() {
		return ID++;
	}

	/**
	 * Resyncs the static id counter after a bulk load. Was package-private ({@code protected}, reachable from
	 * {@code RankManager} only because it lived in the same {@code gang.rank} package); widened to {@code public}
	 * when {@code Permission} moved to {@code gangland-core} (WS5 G0, step 3) since {@code RankManager} — its one
	 * caller, on a fresh-boot / rank-persistence resync — is now in a different module entirely.
	 */
	public static void setID(int id) {
		Permission.ID = id;
	}

	@Override
	public String toString() {
		return String.format("Permission{usedId=%d, permission='%s'}", usedId, permission);
	}

}
