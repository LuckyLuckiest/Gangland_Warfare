package me.luckyraven.database;

import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.database.DatabaseSettingsProvider;

public class GanglandDatabaseSettings implements DatabaseSettingsProvider {
	@Override
	public boolean isSqliteBackup() {
		return Settings.isSqliteBackup();
	}

	@Override
	public boolean isSqliteFailedMysql() {
		return Settings.isSqliteFailedMysql();
	}

	@Override
	public String getMysqlHost() {
		return Settings.getMysqlHost();
	}

	@Override
	public int getMysqlPort() {
		return Settings.getMysqlPort();
	}

	@Override
	public String getMysqlUsername() {
		return Settings.getMysqlUsername();
	}

	@Override
	public String getMysqlPassword() {
		return Settings.getMysqlPassword();
	}
}
