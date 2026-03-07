package me.luckyraven.util;

import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.utilities.messages.MessagesProvider;

public final class TimeMessages implements MessagesProvider {

	private static TimeMessages instance;

	private TimeMessages() { }

	public static void initialize() {
		if (instance != null) return;

		instance = new TimeMessages();
	}

	public static TimeMessages getInstance() {
		if (instance == null) {
			throw new IllegalStateException("TimeMessages instance not initialized.");
		}

		return instance;
	}

	@Override
	public String getYear() {
		return MessageAddon.YEAR.toString();
	}

	@Override
	public String getWeek() {
		return MessageAddon.WEEK.toString();
	}

	@Override
	public String getDay() {
		return MessageAddon.DAY.toString();
	}

	@Override
	public String getHour() {
		return MessageAddon.HOUR.toString();
	}

	@Override
	public String getMinute() {
		return MessageAddon.MINUTE.toString();
	}

	@Override
	public String getSecond() {
		return MessageAddon.SECOND.toString();
	}
}
