package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.bootstrap.GanglandContext;
import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.core.bean.PostConstruct;
import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.plugin.PluginManager;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.user.UserDataLoader;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.bounty.BountySettings;
import me.luckyraven.gang.contract.*;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.rank.RankManager;
import me.luckyraven.gang.user.UserFactory;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.gang.wanted.WantedSettings;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * CONFIG-phase wiring for the data layer: user / rank / gang / member / plugin / waypoint managers, plus the permission
 * propagation step that collects every Bukkit permission with the plugin's prefix.
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

	/**
	 * Both UserManager beans declare {@code MemberManager} as a parameter purely for ordering. The constructor doesn't
	 * consume it, but the dep edge forces BeanGraph to topologically sort {@code UserManager} <b>after</b>
	 * {@link MemberManager} (and transitively after {@link GangManager} / {@link RankManager}), so by the time user
	 * loading runs in {@code PlayerBootstrapService} the member / gang caches are already populated. Dropping the
	 * parameter re-creates the pre-0.8.0 bug where members loaded after users and every member's gang link self-healed
	 * to -1 on startup.
	 */
	@Bean(name = "online", isGeneric = true)
	public UserManager<Player> userManager(RepositoryRegistry repositoryRegistry,
	                                       UserFactory userFactory,
	                                       @SuppressWarnings("unused") MemberManager orderingDep) {
		return new UserManager<>(gangland, repositoryRegistry, userFactory);
	}

	@Bean(name = "offline", isGeneric = true)
	public UserManager<OfflinePlayer> offlineUserManager(RepositoryRegistry repositoryRegistry,
	                                                     UserFactory userFactory,
	                                                     @SuppressWarnings("unused") MemberManager orderingDep) {
		return new UserManager<>(gangland, repositoryRegistry, userFactory);
	}

	/**
	 * Impl-side loader that hydrates a fresh {@link me.luckyraven.gang.user.User} from the DB. Lives here because it
	 * reaches into concrete {@code UserTable} / {@code BankTable} — specifically
	 * {@code BankTable.searchCriteria(User)}, which isn't on the abstract Table contract. {@link UserManager} in the
	 * gang module stays table-agnostic.
	 */
	@Bean
	public UserDataLoader userDataLoader(GanglandDatabase database,
	                                     MemberManager memberManager,
	                                     BountySettings bountySettings,
	                                     WantedSettings wantedSettings) {
		return new UserDataLoader(gangland, database, memberManager, bountySettings, wantedSettings);
	}

	@Bean
	public PluginManager pluginManager(GanglandDatabase database) {
		return new PluginManager(database);
	}

	@Bean
	public RankManager rankManager(RepositoryRegistry repositoryRegistry,
	                               PermissionRegistryContract permissionRegistry) {
		return new RankManager(repositoryRegistry, permissionRegistry);
	}

	@Bean
	public GangManager gangManager(RepositoryRegistry repositoryRegistry,
	                               GangAllianceRepositoryContract allianceRepository) {
		IRepository<Gang> gangRepository = repositoryRegistry.getRepository(Gang.class);
		return new GangManager(gangRepository, allianceRepository);
	}

	@Bean
	public MemberManager memberManager(GanglandDatabase database,
	                                   MemberRepositoryContract memberRepository,
	                                   GangLookupContract gangLookup,
	                                   RankLookupContract rankLookup) {
		return new MemberManager(gangland, database, memberRepository, gangLookup, rankLookup);
	}

	@Bean
	public WaypointManager waypointManager(GanglandDatabase database, PermissionManager permissionManager) {
		return new WaypointManager(gangland, database, permissionManager);
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
	}
}
