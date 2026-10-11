package org.luckyraven.gangland.gang;

import lombok.CustomLog;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.events.gang.GangLevelUpEvent;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.LoadOutcome;
import org.luckyraven.gangland.gang.contract.GangMigrationsContract;
import org.luckyraven.gangland.gang.database.repositories.meta.GangMigrationRepository;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@CustomLog
public class GangManager implements BeanLifecycle, GangLookupContract {

	private final IRepository<Gang>              gangRepository;
	private final GangAllianceRepositoryContract allianceRepository;
	private final Map<Integer, Gang>             gangs;

	/** D7 (0.17.0): one-shot migration id that makes every stored gang invite-only. */
	public static final String STATE_MIGRATION = "0.17.0-gang-state-invite";

	private final GangMigrationsContract migrations;
	private final GangLevelTable         levelTable;
	private       boolean                loaded;

	public GangManager(IRepository<Gang> gangRepository,
	                   GangAllianceRepositoryContract allianceRepository) {
		this(gangRepository, allianceRepository, GangMigrationsContract.NONE);
	}

	public GangManager(IRepository<Gang> gangRepository,
	                   GangAllianceRepositoryContract allianceRepository,
	                   GangMigrationsContract migrations) {
		this(gangRepository, allianceRepository, migrations, new GangLevelTable());
	}

	public GangManager(IRepository<Gang> gangRepository,
	                   GangAllianceRepositoryContract allianceRepository,
	                   GangMigrationsContract migrations, GangLevelTable levelTable) {
		this.gangRepository     = gangRepository;
		this.allianceRepository = allianceRepository;
		this.migrations         = migrations;
		this.levelTable         = levelTable;
		this.gangs              = new HashMap<>();
	}

	public void initialize() {
		// Wire the alliance repo with a live Gang-lookup so its doLoadAll can resolve Gang refs and drop orphans.
		allianceRepository.setGangLookup(this);

		Map<Integer, Gang> gangLookup = gangRepository.loadAll()
				.stream().collect(Collectors.toMap(Gang::getId, Function.identity()));

		gangs.putAll(gangLookup);

		// Alliances come back with live Gang references — just attach each to its owning side
		for (GangAlliance alliance : allianceRepository.loadAll()) {
			alliance.gang().addAlly(alliance);
		}

		// Set data suppliers so repositoryRegistry.saveAll() can persist gangs and alliances
		gangRepository.setDataSupplier(gangs::values);
		allianceRepository.setDataSupplier(this::buildAllAlliances);

		// D7 (0.17.0): every stored gang becomes invite-only once; from now on only /glw gang recruiting open opens one.
		if (gangRepository instanceof LoadOutcome outcome && outcome.lastLoadFailed()) {
			// T-249: a failed read is not an empty table; migrating now would mark D7 done over zero gangs
			log.warn("Gang table did not load; the D7 invite-only migration waits for a boot that reads it");
		} else if (!migrations.isDone(STATE_MIGRATION)) {
			for (Gang gang : gangs.values()) {
				gang.setState(Gang.State.INVITE);
			}
			GangMigrationRepository.saveThen(gangRepository, new ArrayList<>(gangs.values()),
			                                 () -> migrations.markDone(STATE_MIGRATION));
			log.info("Gang states migrated: every gang is invite-only (D7); /glw gang recruiting open opens one");
		}
	}

	/**
	 * Adds XP to the gang and levels it up while the next level is free (fee 0 in gang_rules.yml Levels; a level with a
	 * fee is claimed with /glw gang upgrade confirm and the XP is only banked), firing one {@link GangLevelUpEvent} per
	 * level gained, each after that
	 * level's increment (so a listener reads the level just reached). Cancelling an event undoes that one level and
	 * stops further levels in this call, so the gang stays at the level before that step. A non-finite or non-positive
	 * amount does nothing; a gang at its maximum level gains no XP and fires nothing. No save call: autosave writes the
	 * gang row.
	 */
	@Override
	public void addExperience(Gang gang, double amount) {
		if (gang == null || !Double.isFinite(amount) || amount <= 0) {
			return;
		}

		Level level = gang.getLevel();
		level.addExperience(amount, false, null);

		// T-254: one GangLevelUpEvent per level, each after its own increment. Only levels whose fee is 0 are taken
		// here; a level with a fee (D16 C) waits for /glw gang upgrade confirm and its XP stays banked. The bar is
		// checked here because Level.addLevels itself levels below it.
		while (level.getLevelValue() < level.getMaxLevel()
		       && levelTable.row(level.nextLevel()).cost() <= 0
		       && level.getExperience() >= level.experienceCalculation(level.nextLevel())) {
			if (level.addLevels(1, new GangLevelUpEvent(false, gang, level)) == 0) break;
		}
	}

	public void add(Gang gang) {
		gangs.put(gang.getId(), gang);
	}

	public boolean remove(Gang gang) {
		Gang g = gangs.remove(gang.getId());
		return g != null;
	}

	public void clear() {
		gangs.clear();
	}

	@Override
	public void onClear() {
		clear();
	}

	/**
	 * Keystone's BeanFactory calls this once per bean registration; only the first load initializes, so the D7
	 * migration cannot run twice (its {@code markDone} is async and would not have landed yet).
	 */
	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad && loaded) return;

		loaded = true;
		initialize();
	}

	public boolean contains(Gang gang) {
		return gangs.containsKey(gang.getId());
	}

	public Gang getGang(int id) {
		return gangs.get(id);
	}

	public int size() {
		return gangs.size();
	}

	public Map<Integer, Gang> getGangs() {
		return Collections.unmodifiableMap(gangs);
	}

	/**
	 * Breaks the alliance between two gangs on both sides — in memory <em>and</em> in the database.
	 *
	 * <p>The autosave path is upsert-only ({@code saveAll} → {@code TableBackend.upsertAll} over
	 * {@link #buildAllAlliances()}), so a row that is only dropped from memory is never removed from
	 * {@code gang_ally} and the alliance comes back on the next {@code loadAll} (GR-06). Every caller that ends an
	 * alliance must go through this method instead of calling {@link Gang#removeAlly(Gang)} directly.
	 *
	 * @param first
	 * 		one side of the alliance; ignored when {@code null}.
	 * @param second
	 * 		the other side of the alliance; ignored when {@code null}.
	 */
	public void breakAlliance(@Nullable Gang first, @Nullable Gang second) {
		if (first == null || second == null) return;

		deleteAllianceRows(first, second);
		deleteAllianceRows(second, first);

		first.removeAlly(second);
		second.removeAlly(first);
	}

	// --- GangLookupContract ----------------------------------------------------------------------

	@Override
	public @Nullable Gang findById(int gangId) {
		return gangs.get(gangId);
	}

	@Override
	public Collection<Gang> getAll() {
		return Collections.unmodifiableCollection(gangs.values());
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Deletes every persisted alliance row pointing from {@code owner} to {@code ally}. Both directions are stored as
	 * separate rows, so {@link #breakAlliance(Gang, Gang)} calls this once per direction.
	 */
	private void deleteAllianceRows(Gang owner, Gang ally) {
		for (GangAlliance alliance : new ArrayList<>(owner.getAllies())) {
			if (alliance.ally().getId() != ally.getId()) continue;

			allianceRepository.delete(alliance);
		}
	}

	private Collection<GangAlliance> buildAllAlliances() {
		List<GangAlliance> allies = new ArrayList<>();

		for (Gang gang : gangs.values()) allies.addAll(gang.getAllies());

		return allies;
	}
}
