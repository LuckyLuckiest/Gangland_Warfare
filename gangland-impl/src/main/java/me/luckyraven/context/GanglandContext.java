package me.luckyraven.context;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.CommandTabCompleter;
import me.luckyraven.file.configuration.SettingsLookupImpl;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.util.autowire.DependencyContainer;
import me.luckyraven.util.autowire.bean.BeanFactory;
import me.luckyraven.util.autowire.bean.Phase;
import org.bukkit.command.PluginCommand;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Single root for the entire plugin's wiring. Owns the only {@link DependencyContainer} that exists at runtime, owns
 * the {@link BeanFactory} that drives the phased bootstrap, and runs the post-bootstrap listener + command scans.
 *
 * <p>The bootstrap pipeline is:
 * <ol>
 *     <li><b>Seed:</b> {@code Initializer.postInitialize()} constructs the kernel objects (Gangland, FileManager,
 *     PermissionManager, CompatibilityWorker, PlaceholderService, …) up-front for failure isolation, then registers
 *     each via {@link #register(Class, Object)} so {@code @Configuration} classes can constructor-inject them.</li>
 *     <li><b>Scan:</b> {@link #bootstrap()} scans {@code me.luckyraven.config} for {@code @Configuration} classes and
 *     hands them to {@link BeanFactory}.</li>
 *     <li><b>Instantiate:</b> {@link BeanFactory#instantiate()} runs each {@link Phase} in declared order.
 *     {@link GanglandContext} pre-installs two phase hooks before invocation:
 *     <ul>
 *         <li>{@link Phase#FILE}: after every file-initializer bean, call {@link FileManager#initializeAll()} so the
 *         file is loaded before the next file bean reads it (preserves the staged behavior of the legacy
 *         {@code Initializer.addonsLoader()}).</li>
 *         <li>{@link Phase#DATABASE}: after each database bean, walk every {@link RepositoryRegistry} present in the
 *         container and publish every {@link IRepository} into the container by its concrete class. Uses an identity
 *         set to make repeat invocations idempotent so per-bean firing doesn't double-register.</li>
 *     </ul></li>
 *     <li><b>Lifecycle:</b> {@link BeanFactory} runs {@code @PostConstruct} on every config + bean, then walks every
 *     bean and invokes any zero-arg {@code void initialize()} method (replacing the dozens of explicit
 *     {@code .initialize()} calls in the legacy initializer).</li>
 *     <li><b>Listeners:</b> {@link #bootstrap()} pulls the {@link ListenerManager} bean out of the container and
 *     calls {@code scanAndRegisterListeners} — which now constructor-injects every {@code @ListenerHandler} class
 *     from the same root container.</li>
 *     <li><b>Commands:</b> Same shape: pull {@link CommandManager} from the container, scan, bind the resulting
 *     executor and tab completer to the {@link PluginCommand}.</li>
 * </ol>
 *
 * <p>The container is intentionally exposed via {@link #getContainer()} for code paths that still need direct
 * access during the migration window (e.g. {@code Initializer.hydrateFromContext()}). New code should prefer
 * {@code @Bean} methods or {@link #get(Class)} / {@link #getAll(Class)}.
 */
@CustomLog
public final class GanglandContext {

	private static final String CONFIG_PACKAGE   = "me.luckyraven.config";
	private static final String LISTENER_PACKAGE = "me.luckyraven";
	private static final String COMMAND_PACKAGE  = "me.luckyraven.command.sub";

	@Getter
	private final DependencyContainer container;
	@Getter
	private final BeanFactory         beanFactory;

	private final Gangland gangland;

	/**
	 * Repos already published into the container — guards the per-bean DATABASE hook from double-registering.
	 */
	private final Set<IRepository<?>> publishedRepositories = Collections.newSetFromMap(new IdentityHashMap<>());

	public GanglandContext(Gangland gangland) {
		this.gangland    = gangland;
		this.container   = new DependencyContainer();
		this.beanFactory = new BeanFactory(container, gangland, new SettingsLookupImpl());

		// Self-register so configurations and beans can pull the context / container as a constructor parameter.
		container.registerInstance(GanglandContext.class, this);
		container.registerInstance(DependencyContainer.class, container);
		// Register Gangland explicitly under its concrete type so @Bean methods can take Gangland as a parameter
		// directly (registerInstance walks supertypes too, but listing it here makes the intent obvious).
		container.registerInstance(Gangland.class, gangland);
	}

	/**
	 * Seed a kernel-phase singleton into the container before {@link #bootstrap()} runs. The instance is registered by
	 * its declared type and every superclass / interface (per {@link DependencyContainer#registerInstance}). Calling
	 * this after {@link #bootstrap()} is allowed but defeats the point of phasing.
	 */
	public <T> void register(Class<T> type, T instance) {
		container.registerInstance(type, instance);
	}

	/**
	 * Convenience accessor for legacy code that needs a bean by raw type.
	 */
	public <T> T get(Class<T> type) {
		return container.getInstance(type);
	}

	/**
	 * Drive the phased bean instantiation, then run the listener and command scans. Must be called exactly once, after
	 * every kernel object has been seeded via {@link #register(Class, Object)}.
	 *
	 * <p>The {@code afterBeanCreation} callback runs after every bean phase finishes but BEFORE the lifecycle pass
	 * (PostConstruct + initialize()). It is also fired after every individual bean in every phase via the per-bean
	 * phase hooks below — that incremental fire is what lets a CONFIG-phase {@code @Bean} method (e.g.
	 * {@code DataConfig.memberManager}) call {@code gangland.getInitializer().getGanglandDatabase()} and get a non-null
	 * result, because the hydrate runs after the GanglandDatabase bean is registered in the DATABASE phase but before
	 * the CONFIG phase's beans run. The hydrate is idempotent so re-running it cheaply across many invocations is
	 * fine.
	 */
	public void bootstrap(Runnable afterBeanCreation) {
		FileManager fileManager = container.getInstance(FileManager.class);
		if (fileManager == null) {
			throw new IllegalStateException(
					"GanglandContext.bootstrap(): FileManager must be seeded into the context before bootstrap. "
					+ "Call register(FileManager.class, ...) from Initializer.postInitialize() first.");
		}

		Runnable hydrate = afterBeanCreation != null ? afterBeanCreation : () -> { };

		// FILE phase: after each file-initializer bean is registered, run FileManager.initializeAll() so the file is
		// loaded before the next FILE-phase bean's @Bean method runs (downstream addons typically read settings at
		// construction time, so the staged initializeAll() preserves the legacy addonsLoader() behavior).
		// Then re-run hydrate so Initializer's file-side fields refresh as each addon registers.
		beanFactory.setPhaseHook(Phase.FILE, beans -> {
			fileManager.initializeAll();
			hydrate.run();
		});

		// DATABASE phase: after each database bean, find any RepositoryRegistry in the container and republish every
		// repository into the container by its concrete class so later @Bean parameters of type IRepository<X> (or a
		// concrete repository class) resolve automatically. Idempotent via publishedRepositories identity set.
		// Hydrate runs immediately after so Initializer.ganglandDatabase is populated before the CONFIG phase
		// starts and any manager.initialize() that reads it from the legacy field returns the correct instance.
		beanFactory.setPhaseHook(Phase.DATABASE, beans -> {
			publishRepositoriesFromContainer();
			hydrate.run();
		});

		// CONFIG phase: hydrate after each bean so cross-bean references via gangland.getInitializer().getX() work
		// for any manager whose @Bean method calls .initialize() inline.
		beanFactory.setPhaseHook(Phase.CONFIG, beans -> hydrate.run());

		beanFactory.scan(CONFIG_PACKAGE);
		beanFactory.instantiate(afterBeanCreation);

		runListenerPhase();
		runCommandPhase();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void publishRepositoriesFromContainer() {
		RepositoryRegistry registry = container.getInstance(RepositoryRegistry.class);
		if (registry == null) {
			return;
		}
		for (IRepository<?> repo : registry.getAllRepositories()) {
			if (!publishedRepositories.add(repo)) {
				continue;
			}
			Class repoClass = repo.getClass();
			container.registerInstance(repoClass, repo);
		}
	}

	private void runListenerPhase() {
		ListenerManager listenerManager = container.getInstance(ListenerManager.class);
		if (listenerManager == null) {
			throw new IllegalStateException(
					"GanglandContext.bootstrap(): ListenerManager bean missing. "
					+ "Add a @Bean method that produces ListenerManager to a CONFIG-phase @Configuration class.");
		}
		listenerManager.scanAndRegisterListeners(LISTENER_PACKAGE, gangland);
		listenerManager.registerEvents();
		log.info("Listener phase complete: {} listener(s) registered", listenerManager.getListeners().size());
	}

	private void runCommandPhase() {
		CommandManager commandManager = container.getInstance(CommandManager.class);
		if (commandManager == null) {
			throw new IllegalStateException(
					"GanglandContext.bootstrap(): CommandManager bean missing. "
					+ "Add a @Bean method that produces CommandManager to a CONFIG-phase @Configuration class.");
		}
		PluginCommand command = gangland.getCommand(Gangland.SHORT_PREFIX);
		if (command == null) {
			log.warn("Plugin command /{} not declared in plugin.yml — skipping command bind", Gangland.SHORT_PREFIX);
			return;
		}
		command.setExecutor(commandManager);
		commandManager.scanAndRegisterCommands(COMMAND_PACKAGE, gangland.getClass().getClassLoader());
		command.setTabCompleter(new CommandTabCompleter(CommandManager.getCommands()));
		log.info("Command phase complete: {} command(s) registered", CommandManager.getCommands().size());
	}
}
