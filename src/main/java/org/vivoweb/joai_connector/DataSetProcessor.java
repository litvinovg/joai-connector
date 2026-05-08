/* $This file is distributed under the terms of the license in LICENSE$ */

package org.vivoweb.joai_connector;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataSetProcessor {
	private static final String API_PREFIX = "api/dataRequest/";
	private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
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

	public void processAll(Map<String, Map<String, Set<String>>> configuration) {
		for(Entry<String, Map<String, Set<String>>> entry  : configuration.entrySet()) {
			String name = entry.getKey();
			log.info(String.format("Started updating %s", name));
			processCollection(name, entry.getValue());
			log.info(String.format("Finished updating %s", name));
		}
		
	}

	private void processCollection(String name, Map<String, Set<String>> config) {
		Map<String, Instant> recordList = new RecordsInfo(baseUrl, resultsPerRequest, config, name).getRecordList();
		Set<String> endpointSet = config.get("endpoint");
		if (endpointSet.size() != 1) {
			log.error(String.format("Error. Only one endpoint per collection is supported. Provided %s",
					endpointSet.size()));
			return;
		}
		String endpoint = endpointSet.iterator().next();
		Set<String> processedFileNames = new HashSet<>();
		File collectionDir =  new File(dataDir, encode(name));
		collectionDir.mkdir();
		updateRecords(config, recordList, endpoint, processedFileNames, collectionDir);
		removeNotExistingRecords(collectionDir, processedFileNames);
	}

	private void removeNotExistingRecords(File collectionDir, Set<String> processedFileNames) {
		if (processedFileNames.isEmpty()) {
			log.error("No processed files, skipping removal stage as it is most likely an error.");
			return;
		}
		for (File file : collectionDir.listFiles()) {
			String fileName = file.getName();
			if (!processedFileNames.contains(fileName)) {
				file.delete();
				log.info("Removed " + fileName);
			}
		}
		
	}

	private void updateRecords(Map<String, Set<String>> config, Map<String, Instant> recordList, String endpoint,
			Set<String> processedFileNames, File collectionDir) {
		for (Entry<String, Instant> entry : recordList.entrySet()) {
			String uri = entry.getKey();
			String fileName = getFileName(uri, config);
			processedFileNames.add(fileName);
			File file = new File(collectionDir, fileName);
			try {
				if (!file.exists()) {
					updateRecord(entry, file, endpoint);
				} else {
					Instant fileModTime = Files.getLastModifiedTime(file.toPath()).toInstant();
					Instant receivedModTime = entry.getValue();
					if (fileModTime.isBefore(receivedModTime)) {
						 String lastCheckTime = Storage.get(uri);
						 if (lastCheckTime == null || Instant.parse(lastCheckTime).isBefore(receivedModTime)){
							 updateRecord(entry, file, endpoint);
						 }
					}
				}
			} catch (IOException e) {
				log.error(String.format("Error while updating record %s", file.getAbsolutePath()));
				e.printStackTrace();
			}
		}
	}

	private void updateRecord(Entry<String, Instant> entry, File file, String endpoint) throws IOException {
		String uri = entry.getKey();
		URI recordUrl = createEndpointUrl(endpoint, uri);
		HttpRequest request = HttpRequest.newBuilder(recordUrl).GET().build();
		try {
			HttpResponse<String> response = Http.getClient().send(request, BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				log.error(String.format("Request %s returned code %s.\nSkipping %s record.", recordUrl,
						response.statusCode(), uri));
				return;
			}
			String body = response.body();
			if (!body.startsWith("<rdf:RDF")) {
				log.error(String.format("Received body for %s is not RDF/XML", file.getAbsolutePath()));
				return;
			}
			String fullbody = XML_HEADER + body;
			if(file.exists() && fullbody.equals(Files.readString(file.toPath()))) {
				Storage.add(uri, Instant.now().toString());
				return;
			}
			FileUtils.writeStringToFile(file, fullbody, UTF_8);
			Instant lastModTime = entry.getValue();
			Files.setLastModifiedTime(file.toPath(), FileTime.from(lastModTime));
			log.info(String.format("File %s has been updated.", file.getAbsoluteFile()));
			Storage.remove(uri);

		} catch (Exception e) {
			log.error(String.format("Error while updating record %s", file.getAbsolutePath()));
			e.printStackTrace();
		}
	}

	private URI createEndpointUrl(String endpoint, String uri) {
		return URI.create(baseUrl.toString() + API_PREFIX + Http.encode(endpoint) + "?uri=" + Http.encode(uri)) ;
	}

	private String getFileName(String attrValue, Map<String, Set<String>> config) {
		attrValue = encode(attrValue);
		if (config.containsKey(CONFIG_OPTION_SUFFIX)) {
			attrValue += config.get(CONFIG_OPTION_SUFFIX);
		}
		return attrValue + ".xml";
	}

	private String encode(String attrValue) {
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
		return attrValue;
	}


}
