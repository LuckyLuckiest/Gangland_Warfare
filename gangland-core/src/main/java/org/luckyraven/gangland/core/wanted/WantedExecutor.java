package org.luckyraven.gangland.core.wanted;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.feature.Executor;
import org.luckyraven.keystone.timer.Timer;
import org.luckyraven.gangland.core.events.wanted.WantedEvent;

/**
 * Drives the periodic wanted-level decrease cycle for a single player: the Repeating_Timer safety net.
 * <p>
 * Each tick stays out of the way while an installed {@link WantedDecayPolicy} handles the player's decay, otherwise
 * fires the {@link WantedEvent} and drops one star through {@link WantedStars#drop} with {@link WantedCause#DECAY}
 * (which also charges the star-drop price and sends the messages). Configuration is supplied via
 * {@link WantedSettings} and the owning player's data via {@link WantedContext}, keeping this class free of direct
 * {@code gangland-impl} dependencies.
 */
public class WantedExecutor extends Executor {

	private final WantedEvent    event;
	private final WantedContext  context;
	private final WantedSettings settings;
	private final WantedStars    stars;

	public WantedExecutor(JavaPlugin plugin, WantedEvent event, WantedContext context, WantedSettings settings,
	                      WantedStars stars) {
		super(plugin, "wanted");

		this.event    = event;
		this.context  = context;
		this.settings = settings;
		this.stars    = stars;
	}

	public WantedExecutor(JavaPlugin plugin, WantedEvent event, WantedContext context,
	                      WantedSettings settings) {
		this(plugin, event, context, settings, new WantedStars(plugin, settings));
	}

	@Override
	public Timer createTimer() {
		Wanted wanted = context.getWanted();

		double pow = 1D;
		if (settings.isTimerMultiplierEnabled()) {
			pow = Math.pow(settings.getTimerMultiplierAmount(), wanted.getLevel());
		}

		double time     = settings.getTimerTime() * pow;
		long   interval = (long) time;

		return wanted.createTimer(interval, this::execute);
	}

	@Override
	protected void execute(Timer timer) {
		Wanted wanted = context.getWanted();

		if (isWanted(timer, wanted)) return;
		if (stars.isDecayHandled(wanted)) return;

		// One event instance serves every tick: a listener's cancel applies to this tick only.
		event.setCancelled(false);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) return;

		stars.drop(context, 1, WantedCause.DECAY);

		isWanted(timer, wanted);
	}

	private boolean isWanted(Timer timer, Wanted wanted) {
		if (!wanted.isWanted()) {
			timer.stop();
			return true;
		}
		return false;
	}

}
