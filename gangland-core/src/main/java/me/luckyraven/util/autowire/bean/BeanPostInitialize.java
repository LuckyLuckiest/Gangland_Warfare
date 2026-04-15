package me.luckyraven.util.autowire.bean;

/**
 * Post-init lifecycle contract — runs <b>after</b> every {@link BeanLifecycle#onInitialize(boolean)} call has completed
 * for the current pass (first-load or reload). Use this for beans that need the entire bean graph to be fully
 * initialized before they run, because they trigger cross-cutting side effects (event dispatch, data hydration) that
 * assume every other bean is in a consistent state.
 *
 * <p>Concrete example: {@code PlayerBootstrapService} restores each online player's wanted level via
 * {@code Wanted.setLevel}, which synchronously fires {@code WantedStartEvent}. Handling that event drives
 * {@code CopManager.startSpawnTask}, which reads a lifecycle-wired {@code CopConfigProvider}. If the player bootstrap
 * ran as a regular {@link BeanLifecycle#onInitialize(boolean)}, it would race against
 * {@code CopManager.onInitialize(false)} — the bean graph has no topological edge between them, so
 * {@code configProvider} could still be {@code null} when the event fires, throwing an NPE on {@code /glw reload}.
 *
 * <p>By moving player bootstrap to post-init, every regular lifecycle bean is guaranteed to be fully re-initialized
 * before any events fire from this phase.
 *
 * <p>Execution order within the post-init pass is forward topological (dependencies first), derived from bean
 * registration order as computed by {@link BeanGraph}.
 */
public interface BeanPostInitialize {

	/**
	 * Runs after every {@link BeanLifecycle#onInitialize(boolean)} call for the current pass.
	 *
	 * @param firstLoad {@code true} on first plugin enable; {@code false} on reload.
	 */
	void onPostInitialize(boolean firstLoad);
}