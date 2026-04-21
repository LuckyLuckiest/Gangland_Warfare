package me.luckyraven.bootstrap;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.CommandTabCompleter;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.file.configuration.SettingsLookupImpl;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.util.autowire.DependencyContainer;
import me.luckyraven.util.autowire.bean.BeanFactory;
import me.luckyraven.util.autowire.bean.BeanGraph;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
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
 *     <li><b>Kernel:</b> {@code KernelConfig} ({@link Phase#KERNEL}) produces every bootstrap-critical singleton
 *     (version detection, compatibility, permissions, file management, database, scoreboard) via standard
 *     {@code @Bean} methods. Dependency ordering within the kernel phase is resolved by
 *     {@link BeanGraph} topological sort.</li>
 *     <li><b>Scan:</b> {@link #bootstrap} scans {@code me.luckyraven.config} for {@code @Configuration} classes and
 *     hands them to {@link BeanFactory}.</li>
 *     <li><b>Instantiate:</b> {@link BeanFactory#instantiate()} runs each {@link Phase} in declared order.
 *     {@link GanglandContext} pre-installs two phase hooks before invocation:
 *     <ul>
 *         <li>{@link Phase#FILE}: after every file-initializer bean, call {@link FileManager#initializeAll()} so the
 *         file is loaded before the next file bean reads it.</li>
 *         <li>{@link Phase#DATABASE}: after each database bean, walk every {@link RepositoryRegistry} present in the
 *         container and publish every {@link IRepository} into the container by its concrete class. Uses an identity
 *         set to make repeat invocations idempotent so per-bean firing doesn't double-register.</li>
 *     </ul></li>
 *     <li><b>Lifecycle:</b> {@link BeanFactory} runs {@code @PostConstruct} on every config + bean, then walks every
 *     bean and invokes any zero-arg {@code void initialize()} method.</li>
 *     <li><b>Listeners:</b> {@link #bootstrap} pulls the {@link ListenerManager} bean out of the container and
 *     calls {@code scanAndRegisterListeners} — which constructor-injects every {@code @ListenerHandler} class from
 *     the same root container.</li>
 *     <li><b>Commands:</b> Same shape: pull {@link CommandManager} from the container, scan, bind the resulting
 *     executor and tab completer to the {@link PluginCommand}.</li>
 * </ol>
 *
 * <p>The container is intentionally exposed via {@link #getContainer()} for code paths that need direct access
 * (e.g. qualified bean lookups). New code should prefer {@code @Bean} methods or {@link #get(Class)}.
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
	 * Convenience accessor for legacy code that needs a bean by raw type.
	 */
	public <T> T get(Class<T> type) {
		return container.getInstance(type);
	}

	/**
	 * Runs the reload lifecycle on all beans implementing {@link BeanLifecycle}: {@code onPreClear()} and
	 * {@code onClear()} in reverse topological order, then {@code onInitialize(false)} in forward topological order.
	 * Call this from the reload orchestrator after files have been reloaded and scoreboards have been killed — this
	 * replaces the hard-coded {@code ReloadPlugin.databaseInitialize()} sequence.
	 */
	public void reloadBeans() {
		beanFactory.reloadLifecycleBeans();
	}

	/**
	 * Runs graceful shutdown on all beans implementing {@link BeanLifecycle} in reverse topological order. Call this
	 * from {@code Gangland.onDisable()} <b>before</b> flushing pending data and closing database connections — shutdown
	 * callbacks may convert active sessions into savable state.
	 */
	public void shutdownBeans() {
		beanFactory.shutdownLifecycleBeans();
	}

	/**
	 * Drive the phased bean instantiation, then run the listener and command scans. Must be called exactly once.
	 * {@code KernelConfig} produces all kernel singletons (FileManager, PermissionManager, etc.) during the
	 * {@link Phase#KERNEL} phase before FILE-phase hooks need them.
	 *
	 * <p>Phase hooks are installed before scanning so that FILE-phase beans trigger staged file loading and
	 * DATABASE-phase beans trigger repository republishing into the container.
	 */
	public void bootstrap() {
		// FILE phase: after each file-initializer bean is registered, run FileManager.initializeAll() so the file is
		// loaded before the next FILE-phase bean's @Bean method runs (downstream addons typically read settings at
		// construction time, so the staged initializeAll() preserves the addonsLoader() behavior).
		// FileManager is produced by KernelConfig in the KERNEL phase, so it is guaranteed to be in the container.
		beanFactory.setPhaseHook(Phase.FILE, beans -> {
			FileManager fm = container.getInstance(FileManager.class);
			if (fm != null) {
				fm.initializeAll();
			}
		});

		// DATABASE phase: after each database bean, find any RepositoryRegistry in the container and republish every
		// repository into the container by its concrete class so later @Bean parameters of type IRepository<X> (or a
		// concrete repository class) resolve automatically. Idempotent via publishedRepositories identity set.
		beanFactory.setPhaseHook(Phase.DATABASE, beans -> publishRepositoriesFromContainer());

		beanFactory.scan(CONFIG_PACKAGE);
		beanFactory.instantiate();

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
					this.getClass().getSimpleName() + ".bootstrap(): " + ListenerManager.class.getSimpleName() +
					" bean missing. Add a @Bean method that produces " + ListenerManager.class.getSimpleName() +
					" to a CONFIG-phase @Configuration class.");
		}
		listenerManager.scanAndRegisterListeners(LISTENER_PACKAGE, gangland);
		listenerManager.registerEvents();
		log.debug("Listener phase complete: {} listener(s) registered", listenerManager.getListeners().size());
	}

	private void runCommandPhase() {
		CommandManager commandManager = container.getInstance(CommandManager.class);
		if (commandManager == null) {
			throw new IllegalStateException(
					this.getClass().getSimpleName() + ".bootstrap(): " + CommandManager.class.getSimpleName() +
					" bean missing. Add a @Bean method that produces " + CommandManager.class.getSimpleName() +
					" to a CONFIG-phase @Configuration class.");
		}
		PluginCommand command = gangland.getCommand(Gangland.SHORT_PREFIX);
		if (command == null) {
			log.warn("Plugin command /{} not declared in plugin.yml — skipping command bind", Gangland.SHORT_PREFIX);
			return;
		}
		// Bind the singleton InformationManager to Command's static field BEFORE scanning so subclass constructors
		// that call getCommands() / getCommandInformation() during their own construction see a non-null manager.
		// Threading the manager through every Command subclass super(...) call would touch 25+ files for one read.
		Command.setInformationManager(container.getInstance(InformationManager.class));
		command.setExecutor(commandManager);
		commandManager.scanAndRegisterCommands(COMMAND_PACKAGE, gangland.getClass().getClassLoader());
		command.setTabCompleter(new CommandTabCompleter(CommandManager.getCommands()));
		log.debug("Command phase complete: {} command(s) registered", CommandManager.getCommands().size());
	}
}
