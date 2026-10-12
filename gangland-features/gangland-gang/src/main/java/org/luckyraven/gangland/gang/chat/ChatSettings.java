package org.luckyraven.gangland.gang.chat;

import org.luckyraven.gangland.gang.file.GangRules;

/** The {@code Chat:} section of {@code gang/gang_rules.yml}: console log, public {glw_gang} tag. Live: follows a reload. */
public final class ChatSettings {

	private final GangRules rules;

	private volatile boolean logToConsole;
	private volatile boolean publicTagEnabled;
	private volatile String  noGangTag = "";

	/** Defaults only, no file (tests and the default toggles). */
	public ChatSettings() {
		this.rules = null;
	}

	public ChatSettings(GangRules rules) {
		this.rules = rules;
	}

	public boolean logToConsole() {
		return rules == null ? logToConsole : rules.isChatLogToConsole();
	}

	public boolean publicTagEnabled() {
		return rules == null ? publicTagEnabled : rules.isChatPublicTagEnabled();
	}

	public String noGangTag() {
		return rules == null ? noGangTag : rules.getChatNoGangTag();
	}

	/** Test hook for the no-file form. */
	public void set(boolean logToConsole, boolean publicTagEnabled, String noGangTag) {
		this.logToConsole     = logToConsole;
		this.publicTagEnabled = publicTagEnabled;
		this.noGangTag        = noGangTag;
	}
}
