package org.luckyraven.gangland.fights;

import org.luckyraven.keystone.persistence.FileHandler;

/**
 * Marker of the fights module's KERNEL-phase YAML registration. Beans that read these files take this record as a
 * parameter, which orders them after {@link FightsModuleFileConfig}.
 */
public record FightsModuleFiles(FileHandler rules, FileHandler messages, FileHandler arenas) {
}
