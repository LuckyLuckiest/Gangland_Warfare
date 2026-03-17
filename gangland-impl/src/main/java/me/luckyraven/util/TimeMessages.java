package me.luckyraven.util;

import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.utilities.messages.TimeMessagesProvider;

public final class TimeMessages implements TimeMessagesProvider {

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
		return Messages.YEAR.toString();
	}

	@Override
	public String getWeek() {
		return Messages.WEEK.toString();
	}

	@Override
	public String getDay() {
		return Messages.DAY.toString();
	}

	@Override
	public String getHour() {
		return Messages.HOUR.toString();
	}

	@Override
	public String getMinute() {
		return Messages.MINUTE.toString();
	}

	@Override
	public String getSecond() {
		return Messages.SECOND.toString();
	}
}
