package me.luckyraven.util;

import me.luckyraven.file.configuration.MessageAddon;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class TimeUtil {

	private TimeUtil() { }

	public static String formatTime(long valueInSeconds, boolean customValues) {
		Duration duration = Duration.ofSeconds(valueInSeconds);

		String year   = customValues ? MessageAddon.YEAR.toString() : "year";
		String week   = customValues ? MessageAddon.WEEK.toString() : "week";
		String day    = customValues ? MessageAddon.DAY.toString() : "day";
		String hour   = customValues ? MessageAddon.HOUR.toString() : "hour";
		String minute = customValues ? MessageAddon.MINUTE.toString() : "minute";
		String second = customValues ? MessageAddon.SECOND.toString() : "second";

		long totalDays = duration.toDays();

		long years = totalDays / 365;
		totalDays %= 365;

		long weeks = totalDays / 7;
		long days  = totalDays % 7;

		long hours   = duration.toHoursPart();
		long minutes = duration.toMinutesPart();
		long seconds = duration.toSecondsPart();

		List<String> parts = new ArrayList<>();

		if (years > 0) parts.add(customAppend(years, year));
		if (weeks > 0) parts.add(customAppend(weeks, week));
		if (days > 0) parts.add(customAppend(days, day));
		if (hours > 0) parts.add(customAppend(hours, hour));
		if (minutes > 0) parts.add(customAppend(minutes, minute));
		if (seconds > 0) parts.add(customAppend(seconds, second));

		if (parts.isEmpty()) return "";

		if (parts.size() == 1) {
			return parts.getFirst();
		}

		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
	}

	private static String customAppend(long value, String type) {
		return value + " " + type + ChatUtil.plural((int) value);
	}

}
