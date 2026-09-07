package org.luckyraven.gangland.bootstrap;

import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.command.PluginCommand;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.CommandManager;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.bean.BeanFactory;
import org.luckyraven.keystone.command.CommandTabCompleter;
import org.luckyraven.keystone.command.argument.ArgumentMessages;
import org.luckyraven.keystone.command.brigadier.BrigadierTabRegistrar;
import org.luckyraven.keystone.bean.BeanGraph;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.gangland.file.configuration.SettingsLookupImpl;
import org.luckyraven.gangland.listener.ListenerManager;
import org.luckyraven.keystone.module.LoadedModule;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.update.PluginVersion;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
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
 *     <li><b>Scan:</b> {@link #bootstrap} scans {@code org.luckyraven.gangland.config} for {@code @Configuration} classes and
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
 * <p><b>Runtime modules (0.8.2).</b> Before the scan, Keystone's {@link ModuleLoader} reads every jar in
 * {@code plugins/Gangland_Warfare/modules/}, checks {@code Host_Api} and {@code Depends}, adds the accepted jars to
 * one parent-first classloader and lets each module declare its configurations and packages. Those configurations
 * are registered alongside the core's before {@link BeanFactory#instantiate()}, and the listener, command and
 * repository scans run a second time per module through the module classloader. A faulty module is skipped with a
 * fault; the core never names a module type. Modules load once — a changed folder needs a restart.
 *
 * <p>The container is intentionally exposed via {@link #getContainer()} for code paths that need direct access
 * (e.g. qualified bean lookups). New code should prefer {@code @Bean} methods or {@link #get(Class)}.
 */
@CustomLog
public final class GanglandContext {

	private static final String CONFIG_PACKAGE   = "org.luckyraven.gangland.config";
	private static final String LISTENER_PACKAGE = "org.luckyraven.gangland";
	private static final String COMMAND_PACKAGE  = "org.luckyraven.gangland.command.sub";
	private static final String MODULES_FOLDER   = "modules";

	@Getter
	private final DependencyContainer container;
	@Getter
	private final BeanFactory         beanFactory;
	@Getter
	private final ModuleLoader        moduleLoader;

	private final Gangland gangland;

	/**
	 * Repos already published into the container — guards the per-bean DATABASE hook from double-registering.
	 */
	private final Set<IRepository<?>> publishedRepositories = Collections.newSetFromMap(new IdentityHashMap<>());

	public GanglandContext(Gangland gangland) {
		this.gangland    = gangland;
		this.container   = new DependencyContainer();
		this.beanFactory = new BeanFactory(container, gangland, new SettingsLookupImpl());
		this.moduleLoader = new ModuleLoader(gangland, gangland.getDataFolder().toPath().resolve(MODULES_FOLDER),
		                                     hostApi(gangland));

		// Self-register so configurations and beans can pull the context / container as a constructor parameter.
		container.registerInstance(GanglandContext.class, this);
		container.registerInstance(DependencyContainer.class, container);
		// Register Gangland explicitly under its concrete type so @Bean methods can take Gangland as a parameter
		// directly (registerInstance walks supertypes too, but listing it here makes the intent obvious).
		container.registerInstance(Gangland.class, gangland);
		// The module loader is a kernel object too: DatabaseConfig scans module repository packages through it and
		// KernelConfig merges each module's commands.json into the help index.
		container.registerInstance(ModuleLoader.class, moduleLoader);
	}

	/** Major.minor of the running plugin — what a module's {@code Host_Api} must match to load. */
	private static String hostApi(Gangland gangland) {
		PluginVersion version = PluginVersion.parse(gangland.getDescription().getVersion());
		return version.major() + "." + version.minor();
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
	 * Calls {@code onDisabled()} on every loaded module in reverse load order and closes the module classloader.
	 * Call from {@code Gangland.onDisable()} <b>after</b> {@link #shutdownBeans()} — module beans shut down with the
	 * rest of the pipeline first.
	 */
	public void disableModules() {
		moduleLoader.disableAll();
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

		// Runtime modules: discovered, checked and configured before the scan so their @Configuration classes join
		// the same phased pipeline as the core's. Faults (bad descriptor, wrong Host_Api, missing dependency, a
		// throwing Main) skip that module and are kept in moduleLoader.faults(); the server keeps booting.
		List<LoadedModule> modules = moduleLoader.load();
		log.info("Runtime modules: {} loaded, {} fault(s)", modules.size(), moduleLoader.faults().size());

		beanFactory.scan(CONFIG_PACKAGE);
		for (LoadedModule module : modules) {
			for (Class<?> configuration : module.registrations().configurations()) {
				beanFactory.registerConfiguration(configuration);
			}
		}
		beanFactory.instantiate();

		runListenerPhase();
		runCommandPhase();

		moduleLoader.enableAll(container);
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
		// Module listeners live in jars the plugin loader cannot see: scan their packages through the module loader.
		for (LoadedModule module : moduleLoader.loaded()) {
			for (String listenerPackage : module.registrations().listenerPackages()) {
				listenerManager.scanAndRegisterListeners(listenerPackage, moduleLoader.classLoader());
			}
		}
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
		// Localize the strings the Keystone argument tree emits itself (no-permission, not-implemented, the
		// wrong-arguments prefix). Suppliers, so a /glw reload language switch takes effect immediately. The
		// action-error slot keeps Keystone's default line.
		ArgumentMessages.install(Messages.COMMAND_NO_PERM::toString,
		                         Messages.ARGUMENT_NOT_IMPLEMENTED::toString,
		                         Messages.ARGUMENTS_WRONG::toString,
		                         null);
		// Bind the singleton InformationManager to Command's static field BEFORE scanning so subclass constructors
		// that call getCommands() / getCommandInformation() during their own construction see a non-null manager.
		// Threading the manager through every Command subclass super(...) call would touch 25+ files for one read.
		Command.setInformationManager(container.getInstance(InformationManager.class));
		command.setExecutor(commandManager);
		commandManager.scanAndRegisterCommands(COMMAND_PACKAGE, gangland.getClass().getClassLoader());
		// Top-level @CommandHandler classes a module ships (none for mail, whose commands are contributions).
		for (LoadedModule module : moduleLoader.loaded()) {
			for (String commandPackage : module.registrations().commandPackages()) {
				commandManager.scanAndRegisterCommands(commandPackage, moduleLoader.classLoader());
			}
		}

		// Keystone's completer reads the manager's LIVE view + dev-visibility filter per keystroke (1.7.3 — the old
		// local completer worked off a bootstrap snapshot); the help suggestion appears only where help pages exist.
		CommandTabCompleter tabCompleter = new CommandTabCompleter(commandManager);
		tabCompleter.setHelpSuggestionPredicate(cmd -> cmd instanceof Command ganglandCommand &&
		                                               ganglandCommand.getHelpInfo().size() > 0);
		command.setTabCompleter(tabCompleter);

		// Client-side Brigadier completion (Commodore ships in Keystone.jar; brigadier in the server jar). Safe on
		// plain Spigot — on any failure it logs WARN and the server-side tab completer above stays the only path.
		BrigadierTabRegistrar.registerIfSupported(gangland, command, commandManager);

		log.debug("Command phase complete: {} command(s) registered", commandManager.commandView().size());
	}
}
