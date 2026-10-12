package org.luckyraven.gangland.mail;

import org.luckyraven.keystone.persistence.FileHandler;

/**
 * Marker of the mail module's KERNEL-phase YAML registration. The beans that read these files take this record as a
 * parameter, which is what orders them after {@link MailModuleFileConfig}.
 */
public record MailModuleFiles(FileHandler rules, FileHandler messages, FileHandler messagesEs) {
}
