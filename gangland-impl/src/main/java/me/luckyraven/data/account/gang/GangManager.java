package me.luckyraven.data.account.gang;

import me.luckyraven.Gangland;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.persistence.repository.IRepository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GangManager {

	private final Gangland           gangland;
	private final GanglandDatabase   database;
	private final Map<Integer, Gang> gangs;

	public GangManager(Gangland gangland, GanglandDatabase database) {
		this.gangland = gangland;
		this.database = database;
		this.gangs    = new HashMap<>();
	}

	public void initialize() {
		// get the information from the repositories
		IRepository<Gang> gangRepository = database.getRepositoryRegistry().getRepository(Gang.class);
		IRepository<GangAlliance> gangAllianceRepository = database.getRepositoryRegistry()
		                                                           .getRepository(GangAlliance.class);

		Map<Integer, Gang> gangLookup = gangRepository.loadAll()
				.stream().collect(Collectors.toMap(Gang::getId, Function.identity()));

		gangs.putAll(gangLookup);

		// get the gang alliances and fix them to the each proper gang
		Collection<GangAlliance> gangAlliances = gangAllianceRepository.loadAll();

		// iterate over each gang and verify it with each gang alliance
		for (Gang gang : gangs.values()) {
			List<GangAlliance> alliances = gangAlliances.stream()
					.filter(alliance -> alliance.gang().getId() == gang.getId())
					.map(alliance -> {
						// find the ally gang
						Gang allyGang = gangs.get(alliance.ally().getId());

						// build a new gang alliance
						return new GangAlliance(gang, allyGang, alliance.since());
					})
					.toList();

			gang.addAllAllies(alliances);
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
