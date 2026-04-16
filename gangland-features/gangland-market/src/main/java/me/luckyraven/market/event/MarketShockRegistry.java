package me.luckyraven.market.event;

import me.luckyraven.market.event.events.MarketShockStartedEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds shock templates loaded from {@code market_events.yml} plus the list of currently active (fired) shocks.
 * Thread-safe — the async price ticker reads {@link #active()} every tick while the main thread (commands, scheduled
 * triggers) may call {@link #fire(String)} or {@link #fireAdhoc(MarketShock)}.
 */
public final class MarketShockRegistry {

	private final JavaPlugin                        plugin;
	private final Map<String, MarketShock>          templates = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<MarketShock> active    = new CopyOnWriteArrayList<>();

	public MarketShockRegistry(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Registers a config-defined shock template (startedAtMillis ignored — only target, multiplier, duration matter).
	 */
	public void registerTemplate(MarketShock template) {
		templates.put(template.shockId(), template);
	}

	public Collection<MarketShock> templates() {
		return List.copyOf(templates.values());
	}

	public Optional<MarketShock> template(String id) {
		return Optional.ofNullable(templates.get(id));
	}

	/**
	 * Activates a template by id at the current time.
	 */
	public Optional<MarketShock> fire(String id) {
		MarketShock template = templates.get(id);
		if (template == null) {
			return Optional.empty();
		}

		MarketShock instance = new MarketShock(template.shockId(), template.target(), template.multiplier(),
		                                       template.durationMillis(), System.currentTimeMillis());
		return Optional.of(fireAdhoc(instance));
	}

	/**
	 * Activates an ad-hoc shock (e.g. from {@code /glw market shock}) that doesn't need a template.
	 */
	public MarketShock fireAdhoc(MarketShock instance) {
		active.add(instance);
		Bukkit.getScheduler().runTask(plugin,
		                              () -> Bukkit.getPluginManager().callEvent(new MarketShockStartedEvent(instance)));
		return instance;
	}

	public void pruneExpired() {
		long now = System.currentTimeMillis();
		active.removeIf(s -> !s.isActiveAt(now));
	}

	public Collection<MarketShock> active() {
		return List.copyOf(active);
	}

	public void clear() {
		templates.clear();
		active.clear();
	}
}
