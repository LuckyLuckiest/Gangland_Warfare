package me.luckyraven.copsncrooks.jail;

import lombok.Getter;
import me.luckyraven.persistence.repository.IRepository;
import org.bukkit.Location;

import java.util.UUID;

public class JailManager {

	@Getter
	private final JailService       jailService;
	private final IRepository<Jail> jailRepository;

	public JailManager(JailService jailService, IRepository<Jail> jailRepository) {
		this.jailService    = jailService;
		this.jailRepository = jailRepository;

		initialize();

		// Set data supplier so repository can save current state
		jailRepository.setDataSupplier(jailService::getCells);
	}

	public Jail setJailLocation(int jailId, Location location) {
		Jail jail = jailService.setJailLocation(jailId, location);
		jailRepository.save(jail);
		return jail;
	}

	public void detainPlayer(int jailId, UUID playerId) {
		jailService.detainPlayer(jailId, playerId);
		// Save the updated jail (which now contains this player)
		Jail jail = jailService.getJail(jailId);
		if (jail != null) {
			jailRepository.save(jail);
		}
	}

	public void removeJail(int jailId) {
		Jail jail = jailService.removeJail(jailId);
		if (jail != null) {
			jailRepository.delete(jail);
		}
	}

	public void releasePlayer(UUID playerId) {
		jailService.releasePlayer(playerId);
		// Save all jails since we don't know which one had this player
		jailRepository.saveAll(jailService.getCells());
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
		jailService.clear();
		initialize();
	}

	private void initialize() {
		for (Jail jail : jailRepository.loadAll()) {
			jailService.addJail(jail);
		}
	}
}
