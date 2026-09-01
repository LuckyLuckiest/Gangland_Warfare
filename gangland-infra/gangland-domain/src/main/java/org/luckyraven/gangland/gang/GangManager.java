package org.luckyraven.gangland.gang;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GangManager implements BeanLifecycle, GangLookupContract {

	private final IRepository<Gang>              gangRepository;
	private final GangAllianceRepositoryContract allianceRepository;
	private final Map<Integer, Gang>             gangs;

	public GangManager(IRepository<Gang> gangRepository,
	                   GangAllianceRepositoryContract allianceRepository) {
		this.gangRepository     = gangRepository;
		this.allianceRepository = allianceRepository;
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

	private Collection<GangAlliance> buildAllAlliances() {
		List<GangAlliance> allies = new ArrayList<>();

		for (Gang gang : gangs.values()) allies.addAll(gang.getAllies());

		return allies;
	}
}
