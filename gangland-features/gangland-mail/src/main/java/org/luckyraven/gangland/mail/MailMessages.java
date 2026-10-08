package org.luckyraven.gangland.mail;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * Player-facing text of the mail module, from its own {@code mail/mail_messages.yml} (or {@code _es} when the server
 * language is Spanish). Each getter has an in-code fallback equal to the shipped line.
 */
public class MailMessages extends LocalizedModuleYaml {

	private static final String BASE_NAME = "mail_messages";

	public MailMessages(FileManager fileManager) {
		super(fileManager, BASE_NAME);
	}

	public String inviteOfflineExpires(int hours) {
		return color("Invite_Offline_Expires",
		             "&7They are offline - the invitation stays open for &e%hours% hours&7.")
				.replace("%hours%", String.valueOf(hours));
	}

	public String inviteDeclined(String gang) {
		return color("Invite_Declined", "&7You declined the invitation from &e%gang%&7.").replace("%gang%", gang);
	}

	public String inviteDeclinedNotice(String player) {
		return color("Invite_Declined_Notice", "&e%player% &7declined your gang's invitation.")
				.replace("%player%", player);
	}

	public String inviteDeclineNone() {
		return color("Invite_Decline_None", "&cYou have no pending gang invitation to decline.");
	}

	public String inviteSiblingsCancelled() {
		return color("Invite_Siblings_Cancelled", "&7Your other pending gang invitations were cancelled.");
	}

}
