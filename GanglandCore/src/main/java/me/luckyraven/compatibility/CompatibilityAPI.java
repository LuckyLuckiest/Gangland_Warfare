package me.luckyraven.compatibility;

import lombok.Getter;

public class CompatibilityAPI {

	@Getter private static double  version;
	@Getter private static boolean isPaper;

	static {
		boolean isPaper;

		try {
			Class.forName("com.destroystokyo.paper.VersionHistoryManager$VersionData");
			isPaper = true;
		} catch (ClassNotFoundException exception) {
			isPaper = false;
		}
		CompatibilityAPI.isPaper = isPaper;

//		version = VersionSetup.getVersionAsNumber(VersionSetup.getVersionAsString());

//		if (compatibility == null) {
//			Gangland.getLog4jLogger()
//					.error("Unsupported server version: " + Bukkit.getVersion() + " (" + Bukkit.getBukkitVersion() +
//						   ")",
//						   "Remember that Gangland Warfare supports all major versions 1.9.4+, HOWEVER it doesn't support outdated versions",
//						   "For example, 1.18.1 is NOT a support version, but 1.18.2 IS a supported version",
//						   "If you are running a brand new version of Minecraft, ask LuckyRaven to update the plugin",
//						   "", "!!! CRITICAL ERROR !!!");
//		}
	}

}
