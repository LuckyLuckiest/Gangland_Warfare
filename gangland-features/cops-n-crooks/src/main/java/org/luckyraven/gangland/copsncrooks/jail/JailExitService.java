package org.luckyraven.gangland.copsncrooks.jail;

import org.bukkit.Location;
import org.luckyraven.gangland.core.bean.BeanLifecycle;
import org.luckyraven.gangland.persistence.repository.IRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Coordinates the in-memory {@link JailExitRegistry} with the unified {@code jail_exit} table. The table holds both
 * {@link JailExit.Scope#GLOBAL} (the single universal fallback) and {@link JailExit.Scope#SPECIFIC} (one per jail)
 * rows. Callers use {@link #setExit}/{@link #setGlobalExit} to upsert and {@link #removeExit}/{@link #removeGlobalExit}
 * to delete; loads happen on bean initialisation.
 */
public class JailExitService implements BeanLifecycle {

	private final JailExitRegistry      jailExitRegistry;
	private final IRepository<JailExit> jailExitRepository;

	public JailExitService(JailExitRegistry jailExitRegistry, IRepository<JailExit> jailExitRepository) {
		this.jailExitRegistry   = jailExitRegistry;
		this.jailExitRepository = jailExitRepository;

		jailExitRepository.setDataSupplier(this::snapshotAll);
	}

	public void setExit(int jailId, Location location) {
		jailExitRegistry.setExit(jailId, location);
		jailExitRepository.save(JailExit.forJail(jailId, location));
	}

	public void removeExit(int jailId) {
		jailExitRegistry.clear(jailId);
		jailExitRepository.delete(JailExit.forJail(jailId, null));
	}

	public void setGlobalExit(Location location) {
		jailExitRegistry.setGlobalExit(location);
		jailExitRepository.save(JailExit.global(location));
	}

	public void removeGlobalExit() {
		jailExitRegistry.setGlobalExit(null);
		jailExitRepository.delete(JailExit.global(null));
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		for (JailExit exit : jailExitRepository.loadAll()) {
			Location location = exit.getLocation();
			if (location == null) continue;

			if (exit.isGlobal()) {
				jailExitRegistry.setGlobalExit(location);
			} else if (exit.getJailId() != null) {
				jailExitRegistry.setExit(exit.getJailId(), location);
			}
		}
	}

	@Override
	public void onClear() {
		// Registry is cleared on reload by callers; no-op here so repo state survives.
	}

	private Collection<JailExit> snapshotAll() {
		List<JailExit> all    = new ArrayList<>(jailExitRegistry.snapshot());
		Location       global = jailExitRegistry.getGlobalExit();
		if (global != null) all.add(JailExit.global(global));
		return all;
	}
}
