package me.luckyraven.util.utilities;

import me.luckyraven.util.utilities.messages.TimeMessagesProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class TimeUtil {

	private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

	private TimeUtil() { }

	/**
	 * Converts a fractional number of days to milliseconds. Supports decimal values (e.g., 1.5 days = 36 hours).
	 *
	 * @param days the number of days (can be fractional)
	 *
	 * @return the equivalent time in milliseconds
	 */
	public static long daysToMillis(double days) {
		return (long) (days * MILLIS_PER_DAY);
	}

	/**
	 * Adds a fractional number of days to a timestamp.
	 *
	 * @param timestampMillis the base timestamp in milliseconds
	 * @param days the number of days to add (can be fractional)
	 *
	 * @return the new timestamp in milliseconds
	 */
	public static long addDays(long timestampMillis, double days) {
		return timestampMillis + daysToMillis(days);
	}

	public static String formatTime(long valueInSeconds, boolean customValues, TimeMessagesProvider provider) {
		Duration duration = Duration.ofSeconds(valueInSeconds);

		String year   = customValues ? provider.getYear() : "year";
		String week   = customValues ? provider.getWeek() : "week";
		String day    = customValues ? provider.getDay() : "day";
		String hour   = customValues ? provider.getHour() : "hour";
		String minute = customValues ? provider.getMinute() : "minute";
		String second = customValues ? provider.getSecond() : "second";

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
