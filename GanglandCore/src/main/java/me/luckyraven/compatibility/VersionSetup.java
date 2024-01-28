package me.luckyraven.compatibility;

import lombok.Getter;
import me.luckyraven.Gangland;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Getter
public final class VersionSetup {

	private final String  versionString;
	private final double  version;
	private final boolean paper;

	public VersionSetup() {
		this.versionString = getVersion();
		this.version       = getVersion(versionString);

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
			return Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
		} catch (ArrayIndexOutOfBoundsException exception) {
			Gangland.getLog4jLogger().error(exception);
			return null;
		}
	}

	private double getVersion(String version) {
		version = version.replaceFirst("v", "");
		version = version.replaceFirst("R", "");
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
