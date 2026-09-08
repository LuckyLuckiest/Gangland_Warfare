package org.luckyraven.gangland.turf;

import org.luckyraven.keystone.persistence.FileHandler;

/** The FileHandlers the turf module registers with the host's FileManager. A distinct bean type so no other
 *  module's file registration collides on a bare FileHandler bean. {@code npcs} (turf/turf_npcs.yml) moved here
 *  from cops-n-crooks' CopsNCrooksYamlConfig in group I, alongside the turf-NPC code it configures. */
public record TurfModuleFiles(FileHandler powerups, FileHandler npcs) {
}
