/* $This file is distributed under the terms of the license in LICENSE$ */

package org.vivoweb.joai_connector;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class RecordsInfo {
	private static final String CONFIG_OPTION_FILTER = "filter";
	private static final String FILTERS_PREFIX = "filters";
	private static final String NAME_ATTR = "name";
	private static final String MOD_TIME_ATTR = "modTime";
	private static final String URI_ATTR = "uri";
	private static final String NUM_FOUND_ATTR = "numFound";
	private static final String START_ATTR = "start";
	private static final String DOC_EL = "doc";
	private static final String RESULT_EL = "result";
	private static final String START_INDEX = "&startIndex";
	private static Logger log = LoggerFactory.getLogger(RecordsInfo.class);
	private static final String SEARCH_SUFFIX = "search?xml=1&documentsNumber=";
	private URI baseUrl;
	private String resultsPerRequest;
	private DocumentBuilder builder;
	private Map<String, Set<String>> collectionConfig;
	private String name;

	public RecordsInfo(URI baseUrl, String resultsPerRequest, Map<String, Set<String>> config, String name) {
		try {
			builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			log.error(e.getMessage());
			System.exit(1);
		}
		this.baseUrl = baseUrl;
		this.resultsPerRequest = resultsPerRequest;
		this.collectionConfig = config;
		this.name = name;
	}

	public Map<String, Instant> getRecords() {
		Map<String, Instant> collectionRecords = new HashMap<>();
		Set<String> filters = collectionConfig.get(CONFIG_OPTION_FILTER);
		for (String filter : filters) {
			getFilteredRecords(collectionRecords, filter);	
		}
		return collectionRecords;
	}

	private void getFilteredRecords(Map<String, Instant> collectionRecords, String filter) {
		Map<String, Instant> records = new HashMap<>();
		Long numFound = 0L;
		Long start = 0L;
		Integer foundlength = 0;
		do {
			URI searchUrl = createSearchUrl(start + foundlength, filter);
			HttpRequest request = HttpRequest.newBuilder(searchUrl).GET().build();
			try {
				HttpResponse<String> response = Http.getClient().send(request, BodyHandlers.ofString());
				if (response.statusCode() != 200) {
					log.error(String.format("Request %s returned code %s. Skipping %s set.", searchUrl,
							response.statusCode(), name));
					break;
				}
				String body = response.body();
				Document document = builder.parse(new InputSource(new StringReader(body)));
				Node result = document.getElementsByTagName(RESULT_EL).item(0);
				numFound = Long.parseLong(result.getAttributes().getNamedItem(NUM_FOUND_ATTR).getTextContent().replaceAll("[^0-9]", ""));
				start = Long.parseLong(result.getAttributes().getNamedItem(START_ATTR).getTextContent().replaceAll("[^0-9]", ""));
				NodeList docs = document.getElementsByTagName(DOC_EL);
				foundlength = docs.getLength();
				readRecord(records, foundlength, docs);
			} catch (Exception e) {
				log.error(e.getMessage());
				e.printStackTrace();
				break;
			}
		} while (start < numFound && foundlength > 0);
		collectionRecords.putAll(records);
	}

	private void readRecord(Map<String, Instant> records, Integer foundlength, NodeList docs) {
		for (int i = 0; i < foundlength; i++) {
			Element doc = (Element) docs.item(i);
			NodeList itemNodes = doc.getElementsByTagName("str");
			String uri = null;
			Instant modTime = null;
			int length = itemNodes.getLength();
			for (int j = 0; j < length; j++) {
				Node node = itemNodes.item(j);
				String attrName = node.getAttributes().getNamedItem(NAME_ATTR).getTextContent();
				String attrValue = node.getTextContent();
				if (attrName.equals(URI_ATTR)) {
					uri = attrValue;
				}
				if (attrName.equals(MOD_TIME_ATTR)) {
					modTime = Instant.parse(attrValue);
				}
			}
			if (uri != null && modTime != null) {
				records.put(uri, modTime);
			}
		}
	}

	private URI createSearchUrl(long start, String filter) {
		String url = baseUrl.toString() + SEARCH_SUFFIX + resultsPerRequest;
		url += "&" 
				+ Http.encode(FILTERS_PREFIX) + "=" + Http.encode(filter)
				+ START_INDEX + "=" + start;
		return URI.create(url);
	}
}
