package me.luckyraven.market.bootstrap;

import lombok.CustomLog;
import me.luckyraven.market.contract.MarketSettingsContract;
import me.luckyraven.market.price.MarketPriceEngine;
import me.luckyraven.market.snapshot.SnapshotService;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Owns the async price-tick + daily snapshot timers. Participates in {@link BeanLifecycle} so the server reload and
 * shutdown paths cleanly stop both timers.
 *
 * <p>Both timers start async — the engine is CPU-heavy pure math and the snapshot job does bulk I/O. Neither
 * touches Bukkit world state; any world-visible effect is bounced to the main thread via {@code PriceChangeDispatcher}
 * or {@code EconomyService.dispatchEvent}.
 */
@CustomLog
public final class MarketPriceTicker implements BeanLifecycle {

	private static final long SNAPSHOT_CHECK_MINUTES = 5L;

	private final JavaPlugin             plugin;
	private final MarketPriceEngine      engine;
	private final SnapshotService        snapshot;
	private final MarketSettingsContract settings;

	private RepeatingTimer priceTimer;
	private RepeatingTimer snapshotTimer;
	private LocalDate      lastSnapshotDate;

	public MarketPriceTicker(JavaPlugin plugin,
	                         MarketPriceEngine engine,
	                         SnapshotService snapshot,
	                         MarketSettingsContract settings) {
		this.plugin   = plugin;
		this.engine   = engine;
		this.snapshot = snapshot;
		this.settings = settings;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (!settings.isEnabled()) {
			log.info("Market disabled — price ticker + snapshot service not started.");
			return;
		}

		long priceIntervalTicks = 20L * 60L * Math.max(1, settings.getTickIntervalMinutes());
		priceTimer = new RepeatingTimer(plugin, priceIntervalTicks, timer -> engine.tick());
		priceTimer.start(true);

		long snapshotCheckTicks = 20L * 60L * SNAPSHOT_CHECK_MINUTES;
		snapshotTimer = new RepeatingTimer(plugin, snapshotCheckTicks, timer -> checkSnapshot());
		snapshotTimer.start(true);

		log.info("Market ticker started: every %dm, snapshot check every %dm (target %s)"
						 .formatted(settings.getTickIntervalMinutes(), SNAPSHOT_CHECK_MINUTES,
				                    settings.getSnapshotTime()));
	}

	@Override
	public void onPreClear() {
		stop();
	}

	@Override
	public void onShutdown() {
		stop();
	}

	private void stop() {
		if (priceTimer != null) {
			priceTimer.stop();
			priceTimer = null;
		}
		if (snapshotTimer != null) {
			snapshotTimer.stop();
			snapshotTimer = null;
		}
	}

	private void checkSnapshot() {
		try {
			LocalDateTime now   = LocalDateTime.now(ZoneId.systemDefault());
			LocalDate     today = now.toLocalDate();

			if (lastSnapshotDate != null && !today.isAfter(lastSnapshotDate)) {
				return;
			}

			LocalTime trigger = parseSnapshotTime();
			if (now.toLocalTime().isBefore(trigger) && lastSnapshotDate != null) {
				return;
			}

			snapshot.snapshot();
			lastSnapshotDate = today;
		} catch (Exception e) {
			log.warn("Snapshot check failed: " + e.getMessage());
		}
	}

	private LocalTime parseSnapshotTime() {
		String raw = settings.getSnapshotTime();
		try {
			return LocalTime.parse(raw);
		} catch (Exception e) {
			return LocalTime.MIDNIGHT;
		}
	}
}
