package me.luckyraven.data.account.gang;

import me.luckyraven.Gangland;
import me.luckyraven.core.bean.BeanLifecycle;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.repositories.gang.GangAllianceRepository;
import me.luckyraven.persistence.repository.IRepository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GangManager implements BeanLifecycle {

	private final Gangland           gangland;
	private final GanglandDatabase   database;
	private final Map<Integer, Gang> gangs;

	public GangManager(Gangland gangland, GanglandDatabase database) {
		this.gangland = gangland;
		this.database = database;
		this.gangs    = new HashMap<>();
	}

	public void initialize() {
		IRepository<Gang> gangRepository = database.getRepositoryRegistry().getRepository(Gang.class);
		IRepository<GangAlliance> gangAllianceRepository = database.getRepositoryRegistry()
		                                                           .getRepository(GangAlliance.class);

		Map<Integer, Gang> gangLookup = gangRepository.loadAll()
				.stream().collect(Collectors.toMap(Gang::getId, Function.identity()));

		gangs.putAll(gangLookup);

		// Inject this manager so the alliance repo can resolve real Gang refs and drop orphan rows
		if (gangAllianceRepository instanceof GangAllianceRepository concrete) {
			concrete.setGangManager(this);
		}

		// Alliances come back with live Gang references — just attach each to its owning side
		for (GangAlliance alliance : gangAllianceRepository.loadAll()) {
			alliance.gang().addAlly(alliance);
		}

		// Set data suppliers so repositoryRegistry.saveAll() can persist gangs and alliances
		gangRepository.setDataSupplier(gangs::values);
		gangAllianceRepository.setDataSupplier(this::buildAllAlliances);
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

	@Override
	public void onInitialize(boolean firstLoad) {
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

	private Collection<GangAlliance> buildAllAlliances() {
		List<GangAlliance> allies = new ArrayList<>();

		for (Gang gang : gangs.values()) allies.addAll(gang.getAllies());

		return allies;
	}
}
