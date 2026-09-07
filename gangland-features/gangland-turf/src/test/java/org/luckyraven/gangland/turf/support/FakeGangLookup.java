package org.luckyraven.gangland.turf.support;

import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recording/lookup fake for {@link GangLookupContract}. Gangs are Mockito mocks in every test (real {@code Gang}
 * construction requires {@code GangSettings.bind(...)} to have run, which nothing in this test module does) —
 * this fake just indexes whatever mocks the test hands it by {@code getId()}.
 */
public final class FakeGangLookup implements GangLookupContract {

	private final Map<Integer, Gang> byId = new LinkedHashMap<>();

	public void register(Gang gang) {
		byId.put(gang.getId(), gang);
	}

	@Override
	public Gang findById(int gangId) {
		return byId.get(gangId);
	}

	@Override
	public Collection<Gang> getAll() {
		return new ArrayList<>(byId.values());
	}

	public List<Gang> allGangs() {
		return new ArrayList<>(byId.values());
	}
}
