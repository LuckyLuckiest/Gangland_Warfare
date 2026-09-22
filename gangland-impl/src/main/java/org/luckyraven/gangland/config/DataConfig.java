package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.command.sub.bank.BankCommand;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.economy.GanglandMoneyDropClassifier;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.plugin.PluginManager;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.data.user.UserDataLoader;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.core.bounty.BountySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserFactory;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedKillTrackers;
import org.luckyraven.gangland.core.wanted.WantedSettings;
import org.luckyraven.gangland.item.money.MoneyDropClassifier;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * CONFIG-phase wiring for the data layer: user / plugin / waypoint managers, plus the permission propagation step
 * that collects every Bukkit permission with the plugin's prefix. Rank/gang/member managers moved to the gang
 * module's own {@code GangConfig} (WS5 G1-G3).
 *
 * <p>Both {@link UserManager} beans share the same raw class but differ by generic parameter — they're disambiguated
 * via {@link Qualifier} so consumers downstream can pick the right flavour. {@code @Bean(isGeneric = true)} flags them
 * as parameterized so future strict-mode checks can enforce qualifier-only injection.
 */
@CustomLog
@Configuration
public class DataConfig {

	private final Gangland        gangland;
	private final GanglandContext context;

	public DataConfig(Gangland gangland, GanglandContext context) {
		this.gangland = gangland;
		this.context  = context;
	}

	/**
	 * Surfaces the database's {@link RepositoryRegistry} as a standalone bean so managers in feature modules can inject
	 * it without depending on {@link GanglandDatabase}.
	 */
	@Bean
	public RepositoryRegistry repositoryRegistry(GanglandDatabase database) {
		return database.getRepositoryRegistry();
	}

	@Bean(name = "online", isGeneric = true)
	public UserManager<Player> userManager(RepositoryRegistry repositoryRegistry,
	                                       UserFactory userFactory) {
		return new UserManager<>(gangland, repositoryRegistry, userFactory);
	}

	/**
	 * Linked to the {@code online} manager: both beans hand their data supplier to the <b>same</b>
	 * {@code UserRepository} / {@code BankRepository} instances, so without the link the second {@code initialize()}
	 * would overwrite the first and {@code RepositoryRegistry.saveAll()} would persist only one of the two caches.
	 * The link makes either registration supply the union of both caches.
	 */
	@Bean(name = "offline", isGeneric = true)
	public UserManager<OfflinePlayer> offlineUserManager(RepositoryRegistry repositoryRegistry,
	                                                     UserFactory userFactory,
	                                                     @Qualifier("online") UserManager<Player> onlineUserManager) {
		UserManager<OfflinePlayer> manager = new UserManager<>(gangland, repositoryRegistry, userFactory);

		manager.link(onlineUserManager);

		return manager;
	}

	/**
	 * Impl-side loader that hydrates a fresh {@link User} from the DB. Lives here because it reaches into concrete
	 * {@code UserTable} / {@code BankTable} — specifically {@code BankTable.searchCriteria(User)}, which isn't on the
	 * abstract Table contract. Gang membership is read through the always-present {@link GangMembership} holder
	 * (R9) rather than the gang module's {@code MemberManager} — impl never depends on a module.
	 */
	@Bean
	public UserDataLoader userDataLoader(GanglandDatabase database,
	                                     GangMembership gangMembership,
	                                     BountySettings bountySettings,
	                                     WantedSettings wantedSettings) {
		return new UserDataLoader(gangland, database, gangMembership, bountySettings, wantedSettings);
	}

	@Bean
	public PluginManager pluginManager(GanglandDatabase database) {
		return new PluginManager(database);
	}

	@Bean
	public WaypointManager waypointManager(GanglandDatabase database, PermissionManager permissionManager) {
		return new WaypointManager(gangland, database, permissionManager);
	}

	/**
	 * Seam 1 holder. Always present so {@code MoneyDropListener} constructs whether or not the cops-n-crooks
	 * module is installed; inert (players and vanilla mobs only) until the module installs a delegate. See
	 * documentation/module-loader.md, "Core seams".
	 */
	@Bean
	public GanglandMoneyDropClassifier moneyDropClassifier() {
		return new GanglandMoneyDropClassifier();
	}

	/**
	 * Seam 2 holder. Always present so {@code /glw bank}, the death penalty and the bank placeholders construct;
	 * inert (no caps, no daily limit, no insurance discount, empty tier placeholders) until the cops-n-crooks
	 * module installs a tier lookup. See documentation/module-loader.md, "Core seams".
	 */
	@Bean
	public BankTiers bankTiers() {
		return new BankTiers();
	}

	/**
	 * Seam 3 holder. Always present so {@code EntityDamageListener} constructs; inert (combo-disabled behaviour)
	 * until the cops-n-crooks module installs a delegate. See documentation/module-loader.md, "Core seams".
	 */
	@Bean
	public WantedKillTrackers wantedKillTrackers() {
		return new WantedKillTrackers();
	}

	/**
	 * Collects every Bukkit permission that starts with the plugin's prefix and registers them into the
	 * {@link PermissionManager}. Runs as a {@code @PostConstruct} so it fires after every CONFIG bean is in place but
	 * before LIFECYCLE — the gang manager's {@code initialize()} doesn't depend on the permission set, so timing is
	 * fine.
	 */
	@PostConstruct
	public void registerGanglandPermissions() {
		PermissionManager permissionManager = context.get(PermissionManager.class);
		Set<Permission>   permissions       = Bukkit.getPluginManager().getPermissions();
		Set<String> ganglandPermissions = permissions.stream()
				.map(Permission::getName)
				.filter(name -> name.startsWith(Gangland.FULL_PREFIX))
				.collect(Collectors.toSet());
		permissionManager.addAllPermissions(ganglandPermissions);

		// The two bank permission nodes stay registered whether or not the cops-n-crooks module is installed —
		// they gate core /glw bank deposit|withdraw forms (moved from BankerConfig, T14).
		permissionManager.addPermission(BankTiers.BYPASS_CAP_PERMISSION);
		permissionManager.addPermission(BankCommand.ADMIN_PERMISSION);
	}
}
