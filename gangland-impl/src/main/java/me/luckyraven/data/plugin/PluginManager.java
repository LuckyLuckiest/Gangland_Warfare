package me.luckyraven.data.plugin;

import me.luckyraven.Gangland;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.utilities.TimeUtil;

import java.time.Instant;
import java.util.*;

public class PluginManager {

	private final Gangland         gangland;
	private final List<PluginData> pluginDataList;

	public PluginManager(Gangland gangland) {
		this.gangland       = gangland;
		this.pluginDataList = new ArrayList<>();
	}

	public void initialize() {
		GanglandDatabase        database   = gangland.getInitializer().getGanglandDatabase();
		IRepository<PluginData> repository = database.getRepositoryRegistry().getRepository(PluginData.class);

		Collection<PluginData> loaded = repository.loadAll();

		if (loaded.isEmpty()) {
			Instant    now        = Instant.now();
			long       nowDate    = now.toEpochMilli();
			long       nextScan   = nextPlannedDate(new Date(nowDate)).getTime();
			PluginData pluginData = new PluginData(nowDate, nowDate, nextScan);

			pluginDataList.add(pluginData);
		} else {
			for (PluginData pluginData : loaded) {
				pluginDataList.add(pluginData);
				PluginData.setID(pluginData.getId());
			}
		}

		repository.setDataSupplier(() -> pluginDataList);
	}

	public void clear() {
		pluginDataList.clear();
	}

	public List<PluginData> getPluginDataList() {
		return Collections.unmodifiableList(pluginDataList);
	}

	public Date nextPlannedDate(Date currentDate) {
		long resultMillis = TimeUtil.addDays(currentDate.getTime(), Settings.getCleanUpTime());
		return new Date(resultMillis);
	}

}
