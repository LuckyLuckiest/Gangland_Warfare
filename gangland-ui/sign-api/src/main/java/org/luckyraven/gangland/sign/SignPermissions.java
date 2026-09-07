package org.luckyraven.gangland.sign;

/**
 * Permission nodes guarding plugin signs.
 *
 * <p>Until docket LS-19 there were none: {@code SignCreation} formatted and activated any sign whose first line
 * carried the plugin prefix, so any player who could place a sign could mint a working shop, and any player who
 * could break blocks could delete someone else's. {@code Messages.SIGN_NO_PERM} had been declared for this since
 * the beginning and was never referenced.
 *
 * <p>Observation #19 (lootchests-signs-waypoints.md), docket LS-19.
 */
public final class SignPermissions {

	/** Required to turn a freshly written sign into a working plugin sign. */
	public static final String CREATE = "gangland.sign.create";

	/** Required to break an existing plugin sign. */
	public static final String BREAK = "gangland.sign.break";

	private SignPermissions() {
	}

}
