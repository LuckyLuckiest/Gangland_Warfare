package me.luckyraven.compatibility;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Getter
public final class VersionSetup {

	private static final Logger logger = LogManager.getLogger(VersionSetup.class.getSimpleName());

	@Getter
	private static final String compatibilityFolder = "me.luckyraven.compatibility.version";

	private final String  versionString;
	private final double  versionNumber;
	private final boolean paper;

	public VersionSetup() {
		String checkVersion = getVersion();

		if (checkVersion == null) this.versionString = Version.getLastValidVersion();
		else this.versionString = checkVersion;

		this.versionNumber = getVersion(versionString);

		boolean isPaper;

		try {
			Class.forName("com.destroystokyo.paper.VersionHistoryManager$VersionData");
			isPaper = true;
		} catch (ClassNotFoundException exception) {
			isPaper = false;
		}

		this.paper = isPaper;
	}

	@Nullable
	private String getVersion() {
		try {
			String packageName = Bukkit.getServer().getClass().getPackage().getName();
			String version     = packageName.substring(packageName.lastIndexOf(".") + 1);

			// check for known version structure changes
			if (version.equals("craftbukkit")) {
				// attempt to use reflection to get the version
				logger.warn("Unable to determine the server version... Trying to use bukkit version...");

				version = Version.getBukkitVersion();

				String value = Version.getDeterminedVersion(version);

				// get last valid version
				if (value == null) {
					logger.warn("This version hasn't been tested... Loading latest valid version...");

					version = Version.getLastValidVersion();
				} else {
					logger.info("Found a valid version... {}", value);

					version = value;
				}
			}

			return version;
		} catch (ArrayIndexOutOfBoundsException exception) {
			logger.error(exception);
		}

		return null;
	}

	private double getVersion(String version) {
		version = version.replaceFirst("v", "");
		version = version.replaceFirst("R", "");
		version = version.replaceAll("\\.", "_");

		String[] splitVersion  = version.split("_");
		double   mainVersion   = Double.parseDouble(splitVersion[0]);
		double   subVersion    = Double.parseDouble(splitVersion[1]) / 100;
		double   subSubVersion = Double.parseDouble(splitVersion[2]) / 1000;

		double value = mainVersion + subVersion + subSubVersion;

		if (value % 1 == 0) {
			return (int) value;
		}

		int        intValue   = (int) value;
		BigDecimal bigDecimal = new BigDecimal(value - intValue, new MathContext(3, RoundingMode.HALF_UP));
		bigDecimal = bigDecimal.add(new BigDecimal(intValue));
		bigDecimal = bigDecimal.stripTrailingZeros();
		return Double.parseDouble(bigDecimal.toPlainString());
	}
}
