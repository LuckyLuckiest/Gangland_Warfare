package org.luckyraven.gangland.npcshops.holdup;

import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.FileHandlerReader;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * The {@code Hold_Up:} block of {@code npc/trader_settings.yml}. Every key falls back to {@link #defaults()}.
 */
public record HoldUpSettings(boolean enable, double range, double aimAngle, int fillSeconds, int alarmSeconds,
                             double dislikeMood, int alarmSecondsDisliked, double takeMin, double takeMax,
                             int cooldownSeconds, String filling, String done, String aborted, String closed) {

	private static final HoldUpSettings DEFAULTS = new HoldUpSettings(true, 6.0, 10.0, 10, 10, 0.2, 4, 200.0, 600.0,
	                                                                  300,
	                                                                  "The clerk fills the bag... {seconds}s",
	                                                                  "Got ${take} from the till. Get out!",
	                                                                  "You walked away. The clerk stops filling the bag.",
	                                                                  "This store was robbed recently. Try again later.");

	public static HoldUpSettings defaults() {
		return DEFAULTS;
	}

	public static HoldUpSettings load(FileHandler fileHandler) {
		ConfigReport report = new ConfigReport();
		NodeReader   root   = FileHandlerReader.read(fileHandler, report);

		MappingNode block = root.get("Hold_Up").asMapping().orNull();
		if (block == null) {
			return DEFAULTS;
		}

		HoldUpSettings d = DEFAULTS;
		NodeReader     h = NodeReader.of(block, report);

		MappingNode    messageNode = h.get("Messages").asMapping().orNull();
		NodeReader     m           = messageNode == null ? null : NodeReader.of(messageNode, report);

		int fill  = h.get("Fill_Seconds").asInt().orDefault(d.fillSeconds());
		int alarm = h.get("Alarm_Seconds").asInt().orDefault(d.alarmSeconds());
		double min = h.get("Take_Min").asDouble().orDefault(d.takeMin());
		double max = h.get("Take_Max").asDouble().orDefault(d.takeMax());

		return new HoldUpSettings(h.get("Enable").asBool().orDefault(d.enable()),
		                          h.get("Range").asDouble().orDefault(d.range()),
		                          h.get("Aim_Angle").asDouble().orDefault(d.aimAngle()),
		                          fill <= 0 ? d.fillSeconds() : fill,
		                          alarm <= 0 ? d.alarmSeconds() : alarm,
		                          h.get("Dislike_Mood").asDouble().orDefault(d.dislikeMood()),
		                          h.get("Alarm_Seconds_Disliked").asInt().orDefault(d.alarmSecondsDisliked()),
		                          min,
		                          Math.max(min, max),
		                          h.get("Cooldown_Seconds").asInt().orDefault(d.cooldownSeconds()),
		                          text(m, "Filling", d.filling()),
		                          text(m, "Done", d.done()),
		                          text(m, "Aborted", d.aborted()),
		                          text(m, "Closed", d.closed()));
	}

	private static String text(NodeReader messages, String key, String fallback) {
		if (messages == null) {
			return fallback;
		}
		return messages.get(key).asString().orDefault(fallback);
	}

}
