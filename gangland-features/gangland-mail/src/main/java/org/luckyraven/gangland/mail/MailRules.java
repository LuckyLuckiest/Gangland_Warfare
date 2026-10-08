package org.luckyraven.gangland.mail;

import lombok.Getter;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * Module rules read from {@code mail/mail_rules.yml}. Only {@code Invite_Offline_Expiry_Hours} lives there today: how
 * long an invite to an OFFLINE player stays open. {@code 0} keeps the old never-expires behaviour; a negative value is
 * treated as {@code 0}; a missing key falls back to {@value #DEFAULT_OFFLINE_EXPIRY_HOURS} hours.
 */
public class MailRules implements FileInitializer {

	static final int DEFAULT_OFFLINE_EXPIRY_HOURS = 72;

	private static final String FILE_NAME           = "mail_rules";
	private static final String OFFLINE_EXPIRY_KEY  = "Invite_Offline_Expiry_Hours";

	private final FileHandler fileHandler;

	@Getter
	private int inviteOfflineExpiryHours;

	public MailRules(FileManager fileManager) {
		this.fileHandler = fileManager.getFile(FILE_NAME);
		initialize();
	}

	@Override
	public void initialize() {
		int hours = fileHandler.getFileConfiguration().getInt(OFFLINE_EXPIRY_KEY, DEFAULT_OFFLINE_EXPIRY_HOURS);
		inviteOfflineExpiryHours = Math.max(0, hours);
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

}
