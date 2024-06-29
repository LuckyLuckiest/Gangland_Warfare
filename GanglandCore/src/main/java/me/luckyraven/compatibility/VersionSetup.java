package me.luckyraven.compatibility;

import lombok.Getter;
import me.luckyraven.Gangland;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Getter
public final class VersionSetup {

	@Getter
	private static final String compatibilityFolder = "me.luckyraven.compatibility.version";

	private final String  versionString;
	private final double  versionNumber;
	private final boolean paper;

	public VersionSetup() {
		this.versionString = getVersion();
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

	private String getVersion() {
		try {
			String packageName = Bukkit.getServer().getClass().getPackage().getName();
			String version     = packageName.substring(packageName.lastIndexOf(".") + 1);

			// check for known version structure changes
			if (version.equals("craftbukkit")) {
				// attempt to use reflection to get the version
				Gangland.getLog4jLogger()
						.warn("Unable to determine the server version... Trying to use bukkit version...");

				// try to get the server version from bukkit
				String bukkitVersion    = Bukkit.getVersion().split(" ")[0];
				String processedVersion = bukkitVersion.split("-")[0];

				// fix how the version would look, vX_XX_RX
				StringBuilder builder    = new StringBuilder("v");
				String[]      newVersion = processedVersion.split("\\.");

				builder.append(newVersion[0]).append("_").append(newVersion[1]);

				if (newVersion.length > 2) builder.append("_R").append(newVersion[2]);

				// get the last valid version
//				.append(newVersion[2])

				version = builder.toString();
			}

			return version;
		} catch (ArrayIndexOutOfBoundsException exception) {
			Gangland.getLog4jLogger().error(exception);
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
