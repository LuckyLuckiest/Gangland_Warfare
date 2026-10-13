package org.luckyraven.gangland.gadget.car.theft;

import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.persistence.FileHandler;

/**
 * Hotwire strings read from the module's {@code gadget/gadget_messages.yml}
 * ({@code Hotwire_Progress}, {@code Hotwire_Done}, {@code Hotwire_Cancel}, {@code Hotwire_No_Lockpick}).
 */
public class HotwireMessages {

	private final FileHandler fileHandler;

	public HotwireMessages(FileHandler fileHandler) {
		this.fileHandler = fileHandler;
	}

	/**
	 * Action bar line while the hotwire counts down; {@code %seconds%} is the whole seconds left.
	 */
	public String progress(int secondsLeft) {
		return ChatUtil.color(get("Hotwire_Progress", "&eHotwiring... &f%seconds%s")
				.replace("%seconds%", String.valueOf(secondsLeft)));
	}

	public String done() {
		return ChatUtil.color(get("Hotwire_Done", "&aThe engine roars to life."));
	}

	public String cancel() {
		return ChatUtil.color(get("Hotwire_Cancel", "&cYou gave up hotwiring."));
	}

	public String noLockpick() {
		return ChatUtil.color(get("Hotwire_No_Lockpick", "&cYou need your lockpick for that."));
	}

	private String get(String key, String fallback) {
		return fileHandler.getFileConfiguration().getString(key, fallback);
	}

}
