package org.luckyraven.gangland;

/**
 * Constants a runtime module compiles against without reaching into the host plugin class.
 */
public final class GanglandApi {

	/**
	 * The module API line a {@code module.yml} {@code Host_Api} is matched against. Bump the minor when the host adds
	 * API a module may rely on, the major only on a breaking change; it is independent of the plugin version, so a
	 * patch or feature release of Gangland does not invalidate every module jar on the server.
	 */
	public static final String VERSION = "1.1";

	/**
	 * The permission namespace and long command alias ({@code /gangland}).
	 */
	public static final String FULL_PREFIX = "gangland";

	/**
	 * The primary command label ({@code /glw}).
	 */
	public static final String SHORT_PREFIX = "glw";

	private GanglandApi() {
	}

}
