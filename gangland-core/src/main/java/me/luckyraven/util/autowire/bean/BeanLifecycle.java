package me.luckyraven.util.autowire.bean;

/**
 * Optional lifecycle contract for beans managed by {@link BeanFactory}. Beans that implement this interface participate
 * in the unified reload and shutdown pipelines, driven by the topological order already computed by {@link BeanGraph}.
 *
 * <p>Beans that do <b>not</b> implement this interface (e.g. CommandManager, ListenerManager, pure config objects) are
 * silently skipped during lifecycle passes — they remain simple singletons with no managed lifecycle.
 *
 * <h3>Reload execution order</h3>
 * <ol>
 *     <li>{@link #onPreClear()} — <b>reverse</b> topological order (dependents first)</li>
 *     <li>{@link #onClear()} — <b>reverse</b> topological order</li>
 *     <li>{@link #onInitialize(boolean)} — <b>forward</b> topological order (dependencies first)</li>
 * </ol>
 *
 * <h3>Shutdown execution order</h3>
 * <ol>
 *     <li>{@link #onShutdown()} — <b>reverse</b> topological order</li>
 * </ol>
 */
public interface BeanLifecycle {

	/**
	 * Called before {@link #onClear()} during a reload pass. Use for teardown that must complete before any bean's
	 * cached state is wiped — e.g. stopping active timers, ending scoreboards, or cancelling scheduled tasks whose
	 * callbacks reference data that {@code onClear()} will remove.
	 *
	 * <p>Runs in <b>reverse</b> topological order so dependents tear down before their dependencies.
	 *
	 * <p>Default is a no-op. Only override when you have pre-clear teardown requirements.
	 */
	default void onPreClear() {
	}

	/**
	 * Wipe all in-memory state: clear maps, reset static ID counters, remove cached references. After this call the
	 * bean should be in the same state as immediately after construction (before any {@code onInitialize}).
	 *
	 * <p>Runs in <b>reverse</b> topological order.
	 *
	 * <p>Default is a no-op. Override when the bean holds mutable cached state.
	 */
	default void onClear() {
	}

	/**
	 * Populate the bean from its backing store (database, config files, repositories). Called in <b>forward</b>
	 * topological order so that dependencies are already initialized when this method runs.
	 *
	 * @param firstLoad {@code true} on first plugin enable; {@code false} on reload. Some beans behave differently
	 * 		(e.g. delayed world-wait on first load, immediate on reload).
	 */
	default void onInitialize(boolean firstLoad) {
	}

	/**
	 * Graceful shutdown: stop timers, despawn entities, cancel async tasks, flush pending state. Called in
	 * <b>reverse</b> topological order on plugin disable.
	 *
	 * <p>This is <b>not</b> called during reload — use {@link #onPreClear()} for reload-time teardown.
	 *
	 * <p>Default is a no-op.
	 */
	default void onShutdown() {
	}
}
