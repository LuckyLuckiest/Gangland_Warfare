package org.luckyraven.gangland;

import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.teleportation.WaypointLookupContract;

/**
 * Constants a runtime module compiles against without reaching into the host plugin class, plus (WS6 G1) the
 * {@code ServicesManager} facade an <b>external plugin</b> (not a runtime module — modules already have constructor
 * injection, see {@code documentation/module-loader.md}) resolves to reach the four always-present pieces of
 * Gangland state: {@code Bukkit.getServicesManager().getRegistration(GanglandApi.class)}. Resolve fresh on every
 * call — never cache the reference — the same rule Bartizan's own facade documents, since Gangland may disable,
 * reload or not be installed at all.
 *
 * <p>Exactly four accessors (final ruling R7, {@code plans/WS6-api.md} §0c): {@link #gangs()} returns the
 * core-owned {@link GangMembership} holder directly, never {@code null} and never an {@code Optional} — it is
 * <em>inert</em> (every query answers its documented absent-default) until the {@code gangland-gang} module's
 * {@code GangMembershipInstaller} calls {@code GangMembership.install(...)}. There is no {@code ranks()} accessor:
 * {@code GangLookupContract}/{@code RankLookupContract} live inside {@code gangland-gang}, not in this api, so the
 * facade can never name them.
 */
public interface GanglandApi {

	/**
	 * The module API line a {@code module.yml} {@code Host_Api} is matched against. Bump the minor when the host adds
	 * API a module may rely on, the major only on a breaking change; it is independent of the plugin version, so a
	 * patch or feature release of Gangland does not invalidate every module jar on the server.
	 */
	String VERSION = "2.0";

	/**
	 * The permission namespace and long command alias ({@code /gangland}).
	 */
	String FULL_PREFIX = "gangland";

	/**
	 * The primary command label ({@code /glw}).
	 */
	String SHORT_PREFIX = "glw";

	/**
	 * @return the identity/online-caching lookup — always core-mandatory, never absent.
	 */
	UserLookupContract users();

	/**
	 * @return the core-owned "is/which gang" fact holder — never {@code null}. Every query is inert (documented
	 * absent-default: {@code gangIdOf} → {@code -1}, {@code gangsAllied}/{@code alliedOrSame} → {@code false},
	 * {@code nameOf} → empty) until the {@code gangland-gang} module installs a live view. Check
	 * {@link GangMembership#isInstalled()} to tell "module absent" apart from "module answered no".
	 */
	GangMembership gangs();

	/**
	 * @return the waypoint lookup — always core-mandatory, never absent.
	 */
	WaypointLookupContract waypoints();

	/**
	 * @return the bank-tier holder — already the "optional module" pattern: {@code tierFor(...)} returns
	 * {@code null} until cops-n-crooks installs a lookup.
	 */
	BankTiers bankTiers();

}
