package org.luckyraven.gangland.gang;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.permission.PermissionRegistryContract;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.MemberRepositoryContract;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankParent;
import org.luckyraven.gangland.gang.rank.RankPermission;
import org.luckyraven.gangland.menu.filter.FilterApplier;
import org.luckyraven.gangland.menu.filter.FilterStore;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.BeanFactory;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins T-53 (W55, P0 boot blocker): with the gang module deployed, {@code Gangland.onEnable} threw
 * {@code Failed to instantiate @Configuration class ...GangMembershipInstaller ... Cannot resolve required
 * parameter of type GangMembership}. Root cause: {@code BeanFactory.instantiate()} instantiates every
 * registered {@code @Configuration} class via its own constructor in one up-front pass, entirely
 * <b>before</b> any {@code @Bean} method runs in any phase — a bare {@code @Configuration} class can never
 * resolve a {@code @Bean}-produced parameter, no matter how the configuration classes are ordered. Fixed by
 * making {@code GangMembershipInstaller} a {@code @Bean} produced by {@link GangConfig} instead, whose 3
 * parameters are real ordering edges.
 *
 * <p>Runs the real Keystone {@link BeanFactory} against the real {@link GangConfig} — not a declaration-shape
 * check like {@code GangModuleTest} — plus a minimal in-test stand-in for the one core bean this needs
 * ({@link GangMembership}, normally produced by gangland-impl's {@code IdentityContractConfig}). A literal
 * {@code IdentityContractConfig} can't be used here: {@code gangland-gang} can never depend on
 * {@code gangland-impl} (the module boundary this whole gate protects), so this fixture reproduces the exact
 * same shape of the real bug — {@code GangMembership} produced by a <em>different</em> {@code @Configuration}
 * class than the one that needs it — without violating it. Every other {@code GangConfig} dependency
 * (repositories, plugin, database handler, user manager, filter plumbing) is mocked, matching every other
 * config-class bean test's rig in this codebase.
 */
@DisplayName("GangConfig bean graph - real BeanFactory wiring (T-53)")
class GangConfigBeanGraphTest {

	private DependencyContainer container;
	private BeanFactory         factory;

	@BeforeEach
	void setUp() {
		container = new DependencyContainer();
		JavaPlugin plugin = mock(JavaPlugin.class);
		factory = new BeanFactory(container, plugin, key -> false);

		container.registerInstance(JavaPlugin.class, plugin);
		container.registerInstance(RepositoryRegistry.class, repositoryRegistry());
		container.registerInstance(DatabaseHandler.class, mock(DatabaseHandler.class));
		container.registerInstance(PermissionRegistryContract.class, mock(PermissionRegistryContract.class));
		container.registerInstance(FilterStore.class, mock(FilterStore.class));
		container.registerInstance(FilterApplier.class, mock(FilterApplier.class));

		@SuppressWarnings("unchecked")
		UserManager<Player> userManager = mock(UserManager.class);
		container.registerInstance("online", UserManager.class, userManager);

		factory.registerConfiguration(FixtureGangMembershipProducer.class);
		factory.registerConfiguration(GangConfig.class);
		// Harmless no-op once GangMembershipInstaller isn't @Configuration-annotated any more
		// (BeanFactory.registerConfiguration silently rejects a non-annotated class) — kept so this same test
		// reproduces the pre-fix crash unchanged when the fix is temporarily reverted for the red-first check.
		factory.registerConfiguration(GangMembershipInstaller.class);
	}

	@SuppressWarnings("unchecked")
	private RepositoryRegistry repositoryRegistry() {
		RepositoryRegistry registry = mock(RepositoryRegistry.class);

		IRepository<Gang> gangRepo = mock(IRepository.class);
		when(registry.getRepository(Gang.class)).thenReturn(gangRepo);

		GangAllianceRepositoryContract allianceRepo = mock(GangAllianceRepositoryContract.class);
		when(registry.getRepository(GangAlliance.class)).thenReturn(allianceRepo);

		MemberRepositoryContract memberRepo = mock(MemberRepositoryContract.class);
		when(registry.getRepository(Member.class)).thenReturn(memberRepo);

		// RankManager/MemberManager are both BeanLifecycle — BeanFactory calls onInitialize(true) immediately
		// after each is registered (before the next bean in topo order), so every repository their initialize()
		// touches needs a non-null stand-in too, or the whole instantiate() call NPEs before ever reaching
		// GangMembershipInstaller.
		IRepository<Rank> rankRepo = mock(IRepository.class);
		when(registry.getRepository(Rank.class)).thenReturn(rankRepo);

		IRepository<RankParent> rankParentRepo = mock(IRepository.class);
		when(registry.getRepository(RankParent.class)).thenReturn(rankParentRepo);

		IRepository<Permission> permissionRepo = mock(IRepository.class);
		when(registry.getRepository(Permission.class)).thenReturn(permissionRepo);

		IRepository<RankPermission> rankPermissionRepo = mock(IRepository.class);
		when(registry.getRepository(RankPermission.class)).thenReturn(rankPermissionRepo);

		return registry;
	}

	@Test
	@DisplayName("GangMembershipInstaller's GangMembership dependency resolves and installs a view")
	void gangMembershipInstaller_resolvesAndInstalls() {
		assertDoesNotThrow(() -> factory.instantiate());

		GangMembership membership = container.getInstance(GangMembership.class);
		assertTrue(membership.isInstalled(),
		           "GangMembershipInstaller's @PostConstruct install() must have run against the real bean graph");
	}

	/**
	 * Reproduces the exact pre-fix crash: a bare {@code @Configuration} class construction-injecting a
	 * {@code @Bean}-produced type. Standalone from the test above so a reader can run just this one to see the
	 * failure mode in isolation.
	 */
	@Test
	@DisplayName("a bare @Configuration class construction-injecting a @Bean-produced type fails the same way")
	void bareConfigurationClass_constructorInjectingABeanProducedType_fails() {
		DependencyContainer isolated = new DependencyContainer();
		BeanFactory          isolatedFactory = new BeanFactory(isolated, mock(JavaPlugin.class), key -> false);

		isolatedFactory.registerConfiguration(FixtureGangMembershipProducer.class);
		isolatedFactory.registerConfiguration(BareConfigurationNeedingABeanProducedType.class);

		IllegalStateException thrown =
				assertThrows(IllegalStateException.class, isolatedFactory::instantiate);
		assertTrue(thrown.getMessage().contains("Failed to instantiate @Configuration class"),
		           thrown.getMessage());
	}

	/** Zero-arg producer for {@link GangMembership} — see the class javadoc for why this stands in for impl's
	 * real {@code IdentityContractConfig.gangMembership()} bean. */
	@Configuration
	public static final class FixtureGangMembershipProducer {

		@Bean
		public GangMembership gangMembership() {
			return new GangMembership();
		}
	}

	/** Minimal repro fixture for the second test: any bare {@code @Configuration} class whose constructor names
	 * a {@code @Bean}-produced type hits the same up-front-instantiation hole {@link GangMembershipInstaller}
	 * used to. */
	@Configuration
	public static final class BareConfigurationNeedingABeanProducedType {

		public BareConfigurationNeedingABeanProducedType(GangMembership gangMembership) {
			// unused — the point is that this constructor can never resolve, regardless of body
		}
	}

}
