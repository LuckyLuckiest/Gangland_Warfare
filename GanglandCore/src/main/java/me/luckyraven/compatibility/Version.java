package me.luckyraven.compatibility;

import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

@Getter
public enum Version {

	// support for 1.20.5+
	v1_20_R4(new String[]{"v1_20_R5", "v1_20_R6"}),
	v1_21_R1(new String[]{"v1_21", "v1_21_R1"}),
	v1_21_R2(new String[]{"v1_21_R2", "v1_21_R3"}),
	v1_21_R4(new String[]{"v1_21_R4"}),
	v1_21_R5(new String[]{"v1_21_R5"}),
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

	public static String getLastValidVersion() {
		return versions[versions.length - 1].name();
	}

}
