package me.luckyraven.util;

import me.luckyraven.file.configuration.MessageAddon;

import java.time.Duration;

public class TimeUtil {

	public static String formatTime(long valueInSeconds, boolean customValues) {
		Duration duration = Duration.ofSeconds(valueInSeconds);

		String week   = customValues ? MessageAddon.WEEK.toString() : "week";
		String day    = customValues ? MessageAddon.DAY.toString() : "day";
		String hour   = customValues ? MessageAddon.HOUR.toString() : "hour";
		String minute = customValues ? MessageAddon.MINUTE.toString() : "minute";
		String second = customValues ? MessageAddon.SECOND.toString() : "second";

		long days    = duration.toDays();
		long hours   = duration.toHoursPart();
		long minutes = duration.toMinutesPart();
		long seconds = duration.toSecondsPart();

		long weeks = days / 7;
		days %= 7;

		StringBuilder builder = new StringBuilder();

		if (weeks > 0) customAppend(builder, weeks, week);
		if (days > 0) customAppend(builder, days, day);
		if (hours > 0) customAppend(builder, hours, hour);
		if (minutes > 0) customAppend(builder, minutes, minute);
		if (seconds > 0) customAppend(builder, seconds, second);

		return builder.toString().trim();
	}

	private static void customAppend(StringBuilder builder, long value, String type) {
		builder.append(value).append(" ").append(type).append(ChatUtil.plural((int) value)).append(" ");
	}


}
