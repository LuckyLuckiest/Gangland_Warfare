package me.luckyraven.copsncrooks.jail;

import lombok.Getter;
import me.luckyraven.core.bean.BeanLifecycle;
import me.luckyraven.persistence.repository.IRepository;
import org.bukkit.Location;

import java.util.UUID;

public class JailService implements BeanLifecycle {

	public static int ID = 0;

	@Getter
	private final JailRegistry      jailRegistry;
	private final IRepository<Jail> jailRepository;

	public JailService(JailRegistry jailRegistry, IRepository<Jail> jailRepository) {
		this.jailRegistry   = jailRegistry;
		this.jailRepository = jailRepository;

		// Set the data supplier so the repository can save the current state
		jailRepository.setDataSupplier(jailRegistry::getCells);
	}

	public Jail setJailLocation(Location location, int maxCapacity) {
		ID++;

		Jail jail = jailRegistry.setJailLocation(ID, location, maxCapacity);
		jailRepository.save(jail);
		return jail;
	}

	public void detainPlayer(int jailId, UUID playerId) {
		jailRegistry.detainPlayer(jailId, playerId);
		// Save the updated jail (which now contains this player)
		Jail jail = jailRegistry.getJail(jailId);

		if (jail == null) return;
		jailRepository.save(jail);
	}

	public void removeJail(int jailId) {
		Jail jail = jailRegistry.removeJail(jailId);

		if (jail == null) return;
		jailRepository.delete(jail);
	}

	public void releasePlayer(UUID playerId) {
		jailRegistry.releasePlayer(playerId);
		// Save all jails since we don't know which one had this player
		jailRepository.saveAll(jailRegistry.getCells());
	}

	/**
	 * Save all jails to database
	 */
	public void saveAll() {
		jailRepository.saveAllFromMemory();
	}

	/**
	 * Reloads jail data from the database, clearing the current in-memory state first.
	 */
	public void reload() {
		jailRegistry.clear();
		loadJails();
	}

	@Override
	public void onClear() {
		jailRegistry.clear();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		loadJails();
	}

	private void loadJails() {
		for (Jail jail : jailRepository.loadAll()) {
			jailRegistry.addJail(jail);
		}
	}
}
