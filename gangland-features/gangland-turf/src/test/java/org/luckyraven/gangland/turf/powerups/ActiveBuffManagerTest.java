package org.luckyraven.gangland.turf.powerups;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.turf.support.InMemoryActiveBuffRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link ActiveBuffManager}'s effect aggregation and load-time pruning. Ties to the
 * "ActiveBuffManager.effectiveMultiplier identity values, multiplicative vs additive aggregation, expired-buff
 * exclusion" bullet in turf.md's Test Surface section.
 */
@DisplayName("ActiveBuffManager — effectiveMultiplier aggregation and load-time pruning")
class ActiveBuffManagerTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private InMemoryActiveBuffRepository repository;
	private ActiveBuffManager            manager;

	@BeforeEach
	void setUp() {
		repository = new InMemoryActiveBuffRepository();
		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		manager = new ActiveBuffManager(plugin, repository);
	}

	private static ActiveTurfBuff buff(long id, int turfId, EffectType type, double magnitude, long expiresAt) {
		return new ActiveTurfBuff(id, turfId, type.name().toLowerCase() + "_" + id, type, magnitude, expiresAt);
	}

	@Test
	@DisplayName("effectiveMultiplier returns the multiplicative identity (1.0) with no active buffs")
	void effectiveMultiplier_identityForIncomeMultiplierWithNoBuffs() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			manager.initialize();
			assertEquals(1.0, manager.effectiveMultiplier(1, EffectType.INCOME_MULTIPLIER));
		}
	}

	@Test
	@DisplayName("effectiveMultiplier returns the additive identity (0.0) with no active buffs")
	void effectiveMultiplier_identityForCaptureDefenseBonusWithNoBuffs() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			manager.initialize();
			assertEquals(0.0, manager.effectiveMultiplier(1, EffectType.CAPTURE_DEFENSE_BONUS));
		}
	}

	@Test
	@DisplayName("INCOME_MULTIPLIER buffs stack multiplicatively")
	void effectiveMultiplier_incomeMultiplierStacksMultiplicatively() {
		long farFuture = System.currentTimeMillis() + 3_600_000L;
		repository.seed(buff(1L, 1, EffectType.INCOME_MULTIPLIER, 1.25, farFuture));
		repository.seed(buff(2L, 1, EffectType.INCOME_MULTIPLIER, 1.75, farFuture));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			manager.initialize();
			assertEquals(1.25 * 1.75, manager.effectiveMultiplier(1, EffectType.INCOME_MULTIPLIER), 1e-9);
		}
	}

	@Test
	@DisplayName("CAPTURE_DEFENSE_BONUS buffs sum additively")
	void effectiveMultiplier_captureDefenseBonusSumsAdditively() {
		long farFuture = System.currentTimeMillis() + 3_600_000L;
		repository.seed(buff(1L, 1, EffectType.CAPTURE_DEFENSE_BONUS, 1.0, farFuture));
		repository.seed(buff(2L, 1, EffectType.CAPTURE_DEFENSE_BONUS, 1.0, farFuture));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			manager.initialize();
			assertEquals(2.0, manager.effectiveMultiplier(1, EffectType.CAPTURE_DEFENSE_BONUS), 1e-9);
		}
	}

	@Test
	@DisplayName("a buff of a different effect type on the same turf is not aggregated in")
	void effectiveMultiplier_ignoresOtherEffectTypesOnSameTurf() {
		long farFuture = System.currentTimeMillis() + 3_600_000L;
		repository.seed(buff(1L, 1, EffectType.INCOME_MULTIPLIER, 1.25, farFuture));
		repository.seed(buff(2L, 1, EffectType.GARRISON_DISCOUNT, 0.8, farFuture));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			manager.initialize();
			assertEquals(1.25, manager.effectiveMultiplier(1, EffectType.INCOME_MULTIPLIER), 1e-9);
			assertEquals(0.8, manager.effectiveMultiplier(1, EffectType.GARRISON_DISCOUNT), 1e-9);
		}
	}

	@Test
	@DisplayName("a buff that expired before load is deleted and never counted, even if still in the in-memory list")
	void initialize_deletesAlreadyExpiredBuffsAndExcludesThemFromAggregation() {
		long past = System.currentTimeMillis() - 1_000L;
		repository.seed(buff(1L, 1, EffectType.INCOME_MULTIPLIER, 2.0, past));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			manager.initialize();

			assertEquals(1.0, manager.effectiveMultiplier(1, EffectType.INCOME_MULTIPLIER),
			            "the expired row was dropped at load, so aggregation sees no buffs at all");
			assertTrue(manager.active(1).isEmpty());
			assertEquals(1, repository.deleted.size());
		}
	}

	@Test
	@DisplayName("a buff activated with 0 duration is excluded from aggregation on the very next read, without waiting on prune")
	void effectiveMultiplier_excludesBuffThatExpiresImmediately() {
		PowerupDefinition instantlyExpiring = new PowerupDefinition("blip", "Blip", java.math.BigDecimal.ONE,
		                                                            EffectType.INCOME_MULTIPLIER, 2.0, 0);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			manager.initialize();
			manager.activate(1, instantlyExpiring); // expiresAt = now + 0 -> already <= "now" on the next read

			assertEquals(1.0, manager.effectiveMultiplier(1, EffectType.INCOME_MULTIPLIER),
			            "effectiveMultiplier checks isExpired(now) inline — it does not need prune() to have run");
		}
	}

	@Test
	@DisplayName("activate persists the new buff immediately and folds it into the in-memory map")
	void activate_persistsAndTracksTheNewBuff() {
		PowerupDefinition def = new PowerupDefinition("small_income_boost", "&aSmall Income Boost",
		                                              java.math.BigDecimal.valueOf(5000), EffectType.INCOME_MULTIPLIER,
		                                              1.25, 3600);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			manager.initialize();
			ActiveTurfBuff activated = manager.activate(7, def);

			assertEquals(7, activated.getTurfId());
			assertEquals("small_income_boost", activated.getPowerupId());
			List<ActiveTurfBuff> active = manager.active(7);
			assertEquals(1, active.size());
			assertTrue(repository.loadAll().contains(activated));
		}
	}
}
