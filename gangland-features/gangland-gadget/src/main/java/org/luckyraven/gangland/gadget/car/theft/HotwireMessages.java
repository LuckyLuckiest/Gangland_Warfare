package org.luckyraven.gangland.gadget.car.theft;

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
		return ""; // TDD stub
	}

	public String done() {
		return ""; // TDD stub
	}

	public String cancel() {
		return ""; // TDD stub
	}

	public String noLockpick() {
		return ""; // TDD stub
	}

}
