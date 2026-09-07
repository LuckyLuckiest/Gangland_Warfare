package org.luckyraven.gangland.turf;

import org.luckyraven.keystone.persistence.FileHandler;

/** The FileHandlers the turf module registers with the host's FileManager. A distinct bean type so no other
 *  module's file registration collides on a bare FileHandler bean. */
public record TurfModuleFiles(FileHandler powerups) {
}
