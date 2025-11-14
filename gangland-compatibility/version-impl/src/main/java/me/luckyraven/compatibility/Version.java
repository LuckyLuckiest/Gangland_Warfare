package me.luckyraven.compatibility;

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public enum Version {

	// support for 1.20.5+
	// enum value=craftbukkit version, string values=spigot versions
	v1_20_R4(new String[]{"v1_20_R5", "v1_20_R6"}),
	v1_21_R1(new String[]{"v1_21", "v1_21_R1"}),
	v1_21_R2(new String[]{"v1_21_R2", "v1_21_R3"}),
	v1_21_R3(new String[]{"v1_21_R4"}),
	v1_21_R4(new String[]{"v1_21_R5"}),
	v1_21_R5(new String[]{"v1_21_R6", "v1_21_R7", "v1_21_R8"}),
	v1_21_R6(new String[]{"v1_21_R9", "v1_21_R10"}),
	;

	@Getter(value = AccessLevel.NONE)
	private static final Version[] versions = Version.values();

	private final String[] compatibility;

	Version(String[] compatibility) {
		this.compatibility = compatibility;
	}

	@Nullable
	public static String getDeterminedVersion(String version) {
		for (Version versionEnum : versions) {
			for (String versionStr : versionEnum.compatibility) {
				if (version.equalsIgnoreCase(versionStr)) return versionEnum.name();
			}
		}

		return null;
	}

	@NotNull
	public static String getBukkitVersion() {
		String version;
		// try to get the server version from bukkit
		String bukkitVersion    = Bukkit.getVersion().split(" ")[0];
		String processedVersion = bukkitVersion.split("-")[0];

		// fix how the version would look, vX_XX_RX
		StringBuilder builder    = new StringBuilder("v");
		String[]      newVersion = processedVersion.split("\\.");

		builder.append(newVersion[0]).append("_").append(newVersion[1]);

		if (newVersion.length > 2) builder.append("_R").append(newVersion[2]);

		version = builder.toString();
		return version;
	}

	public static String getLastValidVersion() {
		return versions[versions.length - 1].name();
	}

}
