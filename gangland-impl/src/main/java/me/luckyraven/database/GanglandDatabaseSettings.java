package me.luckyraven.database;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.persistence.database.DatabaseSettingsProvider;

public class GanglandDatabaseSettings implements DatabaseSettingsProvider {
	@Override
	public boolean isSqliteBackup() {
		return SettingAddon.isSqliteBackup();
	}

	@Override
	public boolean isSqliteFailedMysql() {
		return SettingAddon.isSqliteFailedMysql();
	}

	@Override
	public String getMysqlHost() {
		return SettingAddon.getMysqlHost();
	}

	@Override
	public int getMysqlPort() {
		return SettingAddon.getMysqlPort();
	}

	@Override
	public String getMysqlUsername() {
		return SettingAddon.getMysqlUsername();
	}

	@Override
	public String getMysqlPassword() {
		return SettingAddon.getMysqlPassword();
	}
}
