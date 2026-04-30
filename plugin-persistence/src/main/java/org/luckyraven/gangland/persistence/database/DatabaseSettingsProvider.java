package org.luckyraven.gangland.persistence.database;

public interface DatabaseSettingsProvider {

	boolean isSqliteBackup();

	boolean isSqliteFailedMysql();

	String getMysqlHost();

	int getMysqlPort();

	String getMysqlUsername();

	String getMysqlPassword();

}
