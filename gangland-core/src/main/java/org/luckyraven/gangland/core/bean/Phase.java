package org.luckyraven.gangland.core.bean;

/**
 * Bootstrap phases run by {@link BeanFactory#instantiate()} in declared order. Each phase contains its own subgraph of
 * {@link BeanDefinition}s; topological sort runs <i>per phase</i>, so cross-phase ordering is enforced by the pipeline
 * (a CONFIG bean can constructor-inject a FILE bean because the FILE phase has already finished by the time CONFIG
 * runs) while intra-phase ordering follows constructor-edge dependencies.
 *
 * <p>Phase responsibilities:
 * <ul>
 *     <li>{@link #KERNEL} — re-publish the constructor-built kernel objects (Gangland, FileManager, PermissionManager,
 *     CompatibilityWorker, PlaceholderService, …) so downstream phases can constructor-inject them by type.</li>
 *     <li>{@link #FILE} — produce {@link org.luckyraven.gangland.persistence.FileInitializer} implementations. After every
 *     bean in this phase, {@link BeanFactory} calls {@code FileManager.initializeAll()} so the file is loaded before
 *     the next FILE-phase bean tries to read it.</li>
 *     <li>{@link #DATABASE} — produce database + repository registry beans. After this phase, {@link BeanFactory}
 *     walks {@code RepositoryRegistry.getAllRepositories()} and registers each repository instance into the root
 *     container by its concrete class so any later @Bean parameter of type {@code IRepository<X>} resolves.</li>
 *     <li>{@link #CONFIG} — bulk manager phase: data, weapons, signs, items, cops-n-crooks, gadgets, etc.</li>
 *     <li>{@link #LIFECYCLE} — synthetic phase. {@link BeanFactory} walks every bean ever registered, runs
 *     {@code @PostConstruct} methods, then invokes any zero-arg {@code void initialize()} method on the bean. This
 *     replaces the hand-coded {@code .initialize()} calls scattered through the legacy {@code Initializer}.</li>
 *     <li>{@link #LISTENER} — invokes {@code ListenerService.scanAndRegisterListeners(...)} which constructs every
 *     {@code @ListenerHandler} class through the root container and registers it with Bukkit.</li>
 *     <li>{@link #COMMAND} — invokes {@code CommandService.scanAndRegisterCommands(...)} the same way and wires the
 *     resulting executor into the {@code PluginCommand}.</li>
 * </ul>
 */
public enum Phase {
	KERNEL,
	FILE,
	DATABASE,
	CONFIG,
	LIFECYCLE,
	LISTENER,
	COMMAND
}
