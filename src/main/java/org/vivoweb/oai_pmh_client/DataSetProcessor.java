package org.vivoweb.oai_pmh_client;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataSetProcessor {
	private static final String CONFIG_OPTION_SUFFIX = "suffix";
	private static Logger log = LoggerFactory.getLogger(DataSetProcessor.class);
	private URI baseUrl;
	private File dataDir;
	private String resultsPerRequest;

	public DataSetProcessor(File dataDir, URI baseUrl, Integer resultsPerRequest) {
		this.baseUrl = baseUrl;
		this.dataDir = dataDir;
		this.resultsPerRequest = resultsPerRequest.toString();
	}

	public void processAll(Map<String, Map<String, String>> configuration) {
		for(Entry<String, Map<String, String>> entry  : configuration.entrySet()) {
			String name = entry.getKey();
			log.info(String.format("Started updating %s", name));
			processCollection(name, entry.getValue());
			log.info(String.format("Finished updating %s", name));
		}
		
	}

	private void processCollection(String name, Map<String, String> config) {
		Map<String, Instant> recordList = new RecordsInfo(baseUrl, resultsPerRequest, config).getRecordList(name);
		for (Entry<String, Instant> entry : recordList.entrySet()) {
			String uri = entry.getKey();
			File collectionDir =  new File(dataDir, name);
			collectionDir.mkdir();
			File file = new File(collectionDir, getFileName(uri, config));
			if (!file.exists()) {
				updateRecord(entry, file);
			} else {
				try {
					Instant fileModTime = Files.getLastModifiedTime(file.toPath()).toInstant();
					Instant recordTime = entry.getValue();
					if (fileModTime.isBefore(recordTime)) {
						updateRecord(entry, file);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void updateRecord(Entry<String, Instant> entry, File file) {
		System.out.println("Update record " + file.getAbsolutePath());
	}

	private String getFileName(String attrValue, Map<String, String> config) {
		attrValue = attrValue
			.replace("/", "%2F")
			.replace("?", "%3F")
			.replace("#", "%23")
			.replace("=", "%3D")
			.replace("&", "%26")
			.replace(":", "%3A")
			.replace(";", "%3B")
			.replace(" ", "%20")
			.replace("+", "%2B");
		if (config.containsKey(CONFIG_OPTION_SUFFIX)) {
			attrValue += config.get(CONFIG_OPTION_SUFFIX);
		}
		return attrValue + ".xml";
	}


}
