package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.copsncrooks.bounty.BountySettings;
import me.luckyraven.copsncrooks.wanted.WantedSettings;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.plugin.PluginManager;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.TableLookup;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.PostConstruct;
import me.luckyraven.util.autowire.bean.Qualifier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * CONFIG-phase wiring for the data layer: user / rank / gang / member / plugin / waypoint managers, plus the permission
 * propagation step that used to live inline in {@code Initializer.postInitialize()}.
 *
 * <p>Both {@link UserManager} beans share the same raw class but differ by generic parameter — they're disambiguated
 * via {@link Qualifier} so consumers downstream can pick the right flavour. {@code @Bean(isGeneric = true)} flags them
 * as parameterized so future strict-mode checks can enforce qualifier-only injection.
 */
@CustomLog
@Configuration
public class DataConfig {

	private final Gangland          gangland;
	private final PermissionManager permissionManager;

	public DataConfig(Gangland gangland, PermissionManager permissionManager) {
		this.gangland          = gangland;
		this.permissionManager = permissionManager;
	}

	@Bean(name = "online", isGeneric = true)
	public UserManager<Player> userManager(GanglandDatabase database,
	                                       MemberManager memberManager,
	                                       BountySettings bountySettings,
	                                       WantedSettings wantedSettings) {
		UserManager<Player> manager = new UserManager<>(gangland, database, memberManager, bountySettings,
		                                                wantedSettings);
		manager.initialize();
		return manager;
	}

	@Bean(name = "offline", isGeneric = true)
	public UserManager<OfflinePlayer> offlineUserManager(GanglandDatabase database,
	                                                     MemberManager memberManager,
	                                                     BountySettings bountySettings,
	                                                     WantedSettings wantedSettings) {
		UserManager<OfflinePlayer> manager = new UserManager<>(gangland, database, memberManager, bountySettings,
		                                                       wantedSettings);
		manager.initialize();
		return manager;
	}

	@Bean
	public PluginManager pluginManager(GanglandDatabase database) {
		PluginManager manager = new PluginManager(gangland, database);
		manager.initialize();
		return manager;
	}

	@Bean
	public RankManager rankManager(GanglandDatabase database) {
		RankManager manager = new RankManager(gangland, database, permissionManager);
		manager.initialize();
		return manager;
	}

	@Bean
	public GangManager gangManager(GanglandDatabase database) {
		GangManager manager = new GangManager(gangland, database);
		manager.initialize();
		return manager;
	}

	@Bean
	public MemberManager memberManager(GangManager gangManager,
	                                   RankManager rankManager,
	                                   GanglandDatabase database) {
		// rankManager and gangManager are guaranteed initialized here because the topo sort built them first and
		// each of those @Bean methods calls .initialize() inline. Without that, MemberManager.initialize() would
		// dereference an empty rank tree and NPE on rankManager.getRankTree().getRoot().
		MemberManager memberManager = new MemberManager(gangland, database, gangManager, rankManager);
		MemberTable   memberTable   = TableLookup.find(MemberTable.class, database.getTables());
		memberManager.initialize(memberTable);
		return memberManager;
	}

	@Bean
	public WaypointManager waypointManager(GanglandDatabase database) {
		WaypointManager manager = new WaypointManager(gangland, database, permissionManager);
		manager.initialize();
		return manager;
	}

	/**
	 * Mirrors the post-database permission collection step that {@code Initializer.postInitialize()} performed inline.
	 * Runs as a {@code @PostConstruct} so it fires after every CONFIG bean (including the kernel-seeded
	 * {@link PermissionManager}) is in place but before LIFECYCLE — the gang manager's {@code initialize()} doesn't
	 * depend on the permission set, so timing is fine.
	 */
	@PostConstruct
	public void registerGanglandPermissions() {
		Set<Permission> permissions = Bukkit.getPluginManager().getPermissions();
		Set<String> ganglandPermissions = permissions.stream()
				.map(Permission::getName)
				.filter(name -> name.startsWith(Gangland.FULL_PREFIX))
				.collect(Collectors.toSet());
		permissionManager.addAllPermissions(ganglandPermissions);
	}
}
