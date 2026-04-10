package me.luckyraven.persistence;

public interface FileInitializer {

	void initialize();

	FileHandler getFileHandler();

	/**
	 * Clears any cached state so the next {@link #initialize()} starts fresh. Default is a no-op; {@code FileLoader}
	 * subclasses override this with their domain-specific clearing logic.
	 */
	default void clear() {
	}

}
