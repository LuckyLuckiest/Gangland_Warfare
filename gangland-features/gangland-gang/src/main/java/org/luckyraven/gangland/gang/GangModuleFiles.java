package org.luckyraven.gangland.gang;

import org.luckyraven.keystone.persistence.FileHandler;

/**
 * Marker of the gang module's KERNEL-phase YAML registration. The beans that read these files take this record as a
 * parameter, which is what orders them after {@link GangModuleFileConfig}. {@code rules} is gang/gang_rules.yml and
 * {@code messages} is gang/gang_messages.yml, both new in 0.17.0.
 */
public record GangModuleFiles(FileHandler rules, FileHandler messages) {
}
