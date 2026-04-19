package me.luckyraven.persistence.config;

import java.nio.file.Path;

/**
 * A parsed config file.
 *
 * @param root the top-level mapping node. Always a {@link MappingNode}, even when the source had a non-mapping top
 * 		level or failed to parse (in which case it is empty and the associated {@link ConfigReport} carries the error).
 * @param source the file path this document originated from; may be {@code null} for streams.
 */
public record ConfigDocument(MappingNode root, Path source) {

}
