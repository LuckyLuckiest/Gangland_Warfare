package me.luckyraven.persistence.config;

import me.luckyraven.persistence.FileHandler;

import java.util.Collections;

/**
 * One-line factory that extracts the positional document from a {@link FileHandler} and wraps it in a
 * {@link NodeReader}.
 *
 * <p>Used by every migrated loader to skip the three-line boilerplate of
 * extracting {@code parsedDocument().root()} and constructing a reader. When the handler did not produce a document
 * (e.g. the file failed to load) the factory returns a reader over an empty mapping so downstream
 * {@code NodeReader.get(...)} calls remain safe; the failure itself has already been logged by {@link FileHandler}
 * during load.
 */
public final class FileHandlerReader {

	private FileHandlerReader() {
	}

	/**
	 * Build a {@link NodeReader} over the handler's root mapping.
	 *
	 * @param handler the file handler. Its {@link FileHandler#getParsedDocument()} is consulted; a {@code null} or
	 * 		non-mapping root yields an empty reader.
	 * @param report collector for loader-level issues (separate from the handler's own parse report, which already
	 * 		drained YAML-syntax diagnostics at load time).
	 *
	 * @return a reader bound to the handler's root mapping.
	 */
	public static NodeReader read(FileHandler handler, ConfigReport report) {
		ConfigDocument document = handler.getParsedDocument();

		if (document == null) return NodeReader.of(emptyRoot(), report);

		return NodeReader.of(document.root(), report);
	}

	private static MappingNode emptyRoot() {
		SourceLocation loc = new SourceLocation(null, 1, 1, 1, 1);

		return new MappingNode(Collections.emptyMap(), loc, "");
	}

}
