package org.luckyraven.gangland.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.permission.PermissionRegistryContract;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.GangMessageContract;
import org.luckyraven.gangland.gang.contract.GangPermissionBridgeContract;
import org.luckyraven.gangland.gang.contract.GangSettingsContract;
import org.luckyraven.gangland.gang.contract.MemberRepositoryContract;
import org.luckyraven.gangland.gang.contract.RankLookupContract;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.gang.GangItemSourceContribution;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;
import org.luckyraven.gangland.gang.command.debug.GangDebugContribution;
import org.luckyraven.gangland.gang.command.option.GangOptionContribution;
import org.luckyraven.gangland.gang.file.GanglandGangMessages;
import org.luckyraven.gangland.gang.file.GanglandGangPermissionBridge;
import org.luckyraven.gangland.gang.file.GanglandGangSettings;
import org.luckyraven.gangland.gang.file.GanglandRankLookup;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberFilterAdapter;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.menu.GangMenuItemSourceContribution;
import org.luckyraven.gangland.gang.placeholder.GangPlaceholderContribution;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.menu.filter.FilterApplier;
import org.luckyraven.gangland.menu.filter.FilterStore;
import org.bukkit.entity.Player;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * The gang module's own contract and manager wiring (WS5 G1 step 9c + the G2 config-move slice, step 17) — the
 * module-side half of what the deleted {@code GangModuleConfig} used to do in gangland-impl. The identity-side
 * beans (userLookupContract, permissionRegistryContract, identitySettingsContract, the {@code GangMembership}
 * holder) moved to core's {@code IdentityContractConfig} instead; this class wires the 5 wholly-module contracts,
 * the 2 R7 interfaces ({@link GangLookupContract}/{@link RankLookupContract}, now implemented entirely inside
 * this module), and the {@code GangManager}/{@code RankManager}/{@code MemberManager} beans that used to live in
 * gangland-impl's {@code DataConfig}.
 *
 * <p>{@link #gangSettingsContract()} still routes through the api-hosted {@code Settings} class (same
 * {@code settings.yml} {@code Gang:} keys as before this move) rather than the module's own YAML — the "module
 * owns its own YAML directly" end state from the plan (§5) is deferred, not part of this gate; see the gate
 * report.
 */
@Configuration
public final class GangConfig {

	@Bean
	public GangSettingsContract gangSettingsContract() {
		GangSettingsContract contract = new GanglandGangSettings();
		// Bind the static facade so gang data classes (Gang) can read settings from their constructors without
		// injecting the contract.
		GangSettings.bind(contract);
		return contract;
	}

	@Bean
	public GangMessageContract gangMessageContract() {
		return new GanglandGangMessages();
	}

	@Bean
	public GangPermissionBridgeContract gangPermissionBridgeContract() {
		return new GanglandGangPermissionBridge();
	}

	@Bean
	public GangLookupContract gangLookupContract(GangManager gangManager) {
		// GangManager implements GangLookupContract directly — return it as the contract.
		return gangManager;
	}

	@Bean
	public RankLookupContract rankLookupContract(RankManager rankManager) {
		return new GanglandRankLookup(rankManager);
	}

	@Bean
	public GangAllianceRepositoryContract gangAllianceRepositoryContract(RepositoryRegistry registry) {
		return (GangAllianceRepositoryContract) registry.getRepository(GangAlliance.class);
	}

	@Bean
	public MemberRepositoryContract memberRepositoryContract(RepositoryRegistry registry) {
		return (MemberRepositoryContract) registry.getRepository(Member.class);
	}

	@Bean
	public GangManager gangManager(RepositoryRegistry repositoryRegistry,
	                               GangAllianceRepositoryContract allianceRepository) {
		IRepository<Gang> gangRepository = repositoryRegistry.getRepository(Gang.class);
		return new GangManager(gangRepository, allianceRepository);
	}

	@Bean
	public RankManager rankManager(RepositoryRegistry repositoryRegistry,
	                               PermissionRegistryContract permissionRegistry) {
		return new RankManager(repositoryRegistry, permissionRegistry);
	}

	/**
	 * {@code DatabaseHandler} resolves by type against whatever concrete handler the host registers (matching
	 * every repository constructor's own {@code DatabaseHandler}-typed parameter) — the module never names
	 * gangland-impl's concrete {@code GanglandDatabase}.
	 */
	@Bean
	public MemberManager memberManager(JavaPlugin plugin, DatabaseHandler databaseHandler,
	                                   MemberRepositoryContract memberRepository, GangLookupContract gangLookup,
	                                   RankLookupContract rankLookup) {
		return new MemberManager(plugin, databaseHandler, memberRepository, gangLookup, rankLookup);
	}

	/**
	 * Answers {@code gang_*} and the member-touching {@code user_*} placeholders on the core's behalf (WS5 G2 step
	 * 12, S4) — see {@link GangPlaceholderContribution}'s javadoc.
	 */
	@Bean
	public PlaceholderContribution gangPlaceholderContribution(UserManager<Player> userManager,
	                                                           MemberManager memberManager, GangManager gangManager) {
		return new GangPlaceholderContribution(userManager, memberManager, gangManager);
	}

	/**
	 * Attaches {@code /glw option gang rank <target> <rank>} under the core's {@code option} command (WS5 G2
	 * step 14) — see {@link GangOptionContribution}'s javadoc.
	 */
	@Bean
	public CommandContribution gangOptionContribution(JavaPlugin plugin, UserManager<Player> userManager,
	                                                   MemberManager memberManager, GangManager gangManager,
	                                                   RankManager rankManager) {
		return new GangOptionContribution(plugin, userManager, memberManager, gangManager, rankManager);
	}

	/**
	 * Attaches {@code /glw debug gang-data|member-data|rank-data} under the core's {@code debug} command (WS5 G2
	 * step 14) — see {@link GangDebugContribution}'s javadoc.
	 */
	@Bean
	public CommandContribution gangDebugContribution(JavaPlugin plugin, UserManager<Player> userManager,
	                                                  GangManager gangManager, MemberManager memberManager,
	                                                  RankManager rankManager) {
		return new GangDebugContribution(plugin, userManager, gangManager, memberManager, rankManager);
	}

	/**
	 * Projects {@link Gang}/{@link org.luckyraven.gangland.gang.member.Member} onto the impl-owned filter
	 * framework's field axes — see the classes' own javadoc for why these moved (unchanged) into the module
	 * once {@code FilterAdapter}/{@code FilterField}/{@code StandardFilterField} became api-hosted (W54 F1).
	 */
	@Bean
	public GangFilterAdapter gangFilterAdapter() {
		return new GangFilterAdapter();
	}

	@Bean
	public MemberFilterAdapter memberFilterAdapter() {
		return new MemberFilterAdapter();
	}

	/**
	 * Feeds the {@code gangs}/{@code gang_members}/{@code gang_allies} YAML menu item sources (W54 F1) — see
	 * {@link GangMenuItemSourceContribution}'s javadoc. {@code FilterStore}/{@code FilterApplier} are the same
	 * shared bean instances {@code GameplayConfig} constructs in gangland-impl, resolved here by type like any
	 * other cross-boundary bean (e.g. {@code RepositoryRegistry}, {@code PermissionManager}).
	 */
	@Bean
	public GangItemSourceContribution gangItemSourceContribution(UserManager<Player> userManager,
	                                                              GangManager gangManager, FilterStore filterStore,
	                                                              FilterApplier filterApplier,
	                                                              GangFilterAdapter gangFilterAdapter,
	                                                              MemberFilterAdapter memberFilterAdapter) {
		return new GangMenuItemSourceContribution(userManager, gangManager, filterStore, filterApplier,
		                                          gangFilterAdapter, memberFilterAdapter);
	}
}
