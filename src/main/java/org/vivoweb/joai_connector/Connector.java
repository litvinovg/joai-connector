/* $This file is distributed under the terms of the license in LICENSE$ */

package org.vivoweb.joai_connector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class Connector {
	private static final String DIRECTORY_CONFIGURATION_OPTION = "directory";
	private static Options options = new Options();
	private static Logger log = LoggerFactory.getLogger(Connector.class);
	private static final String apiSuffix = "api/dataRequest/";
	private static final String apiConfigSuffix = "OAI PMH Configuration";
	private static final ObjectMapper mapper = new ObjectMapper();
	private static Option URL_OPTION = new Option("url", true, "Vitro/VIVO instance URL");
	private static Option DATA_OPTION = new Option("data", true, "Path to data directory");
	private static Option USER_OPTION = new Option("user", true, "Vitro/VIVO user email");
	private static Option PASS_OPTION = new Option("pass", true, "Vitro/VIVO password");
	private static Option CONFIG_OPTION = new Option("config", true, "Configuration path");
	static {
		options.addOption(CONFIG_OPTION);
		options.addOption(URL_OPTION);
		options.addOption(DATA_OPTION);
		options.addOption(USER_OPTION);
		options.addOption(PASS_OPTION);
	}

	public static void main(String[] args) {
		Properties props = new Properties();
		CommandLineParser parser = new DefaultParser();
		Http.init();
		try {
			CommandLine cmdline = parser.parse(options, args);
			readProperties(props, cmdline);
			URI baseUrl = URI.create(fixUrlSlash(getValue(props, cmdline, URL_OPTION, false)));
			File dataDir = new File(getValue(props, cmdline, DATA_OPTION, false));
			checkDataDir(dataDir);
			String userEmail = getValue(props, cmdline, USER_OPTION, true);
			String userPass = getValue(props, cmdline, PASS_OPTION, true);
			if (userEmail != null && userPass != null) {
				authorize(baseUrl, userEmail, userPass);
			}
			new CollectionProcessor(dataDir, baseUrl, 100).processAll(getConfiguration(baseUrl));
		} catch (ParseException e) {
			log.error(e.getMessage());
		}
	}

	private static String getValue(Properties props, CommandLine cmdline, Option option, boolean optional) {
		String inputValue;
		String optionName = option.getOpt();
		if (cmdline.hasOption(optionName)) {
			inputValue = cmdline.getOptionValue(optionName);
		} else {
			inputValue = props.get(optionName) == null ? null : props.get(optionName).toString();
		}
		if (inputValue == null && !optional) {
			exitAndUsage();
		}
		return inputValue;
	}
	
	private static void readProperties(Properties props, CommandLine cmdline) {
		String config = CONFIG_OPTION.getOpt();
		if (cmdline.hasOption(config)) {
			try (FileInputStream in = new FileInputStream(cmdline.getOptionValue(config))) {
			    props.load(in);
			} catch (IOException e) {
			    log.error(e.getMessage());
			}
		}
	}

	private static Map<String, Map<String, Set<String>>> getConfiguration(URI url) {
		String configurationUrl = url.toString() + apiSuffix + Http.encode(apiConfigSuffix);
		URI configUri = URI.create(configurationUrl);
		HttpRequest request = HttpRequest.newBuilder(configUri).GET().build();
		Map<String, Map<String, Set<String>>> configuration = new HashMap<>();
		try {
			HttpResponse<String> response = Http.getClient().send(request, BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				System.out.println(configUri.toString());
				log.error("VIVO is not accessible. status code " + response.statusCode());
				exit();
			}
			JsonNode jsonResponse = mapper.readTree(response.body());
			JsonNode results = jsonResponse.at("/results/bindings");
			if (!results.isArray()) {
				log.error(String.format("Configuration wasn't returned from %s", configurationUrl));
				exit();
			}
			for (JsonNode data : results.asArray().elements()) {
				if (!data.isObject()) {
					log.error(String.format("Returned configuration has incorrect format %s", configurationUrl));
					exit();
				}
				processConfigurationItem(configuration, data);
			}
		} catch (Exception e) {
			log.error(e.getMessage());
			exit();
		}
		return configuration;
	}

	private static void processConfigurationItem(Map<String, Map<String, Set<String>>> configuration, JsonNode data) {
		ObjectNode sparqlJsonConfiguration = data.asObject();
		String directoryName = sparqlJsonConfiguration.at("/directory/value").asString();
		Map<String, Set<String>> collectionConfiguration = configuration.get(directoryName);
		if (collectionConfiguration == null) {
			collectionConfiguration = new HashMap<>();
			configuration.put(directoryName, collectionConfiguration);
		}
		for (String name : sparqlJsonConfiguration.propertyNames()) {
			if (name.equals(DIRECTORY_CONFIGURATION_OPTION)) {
				continue;
			}
			String value = sparqlJsonConfiguration.at("/" + name + "/value").asString();
			Set<String> set = collectionConfiguration.get(name);
			if (set == null) {
				set = new HashSet<>();
				collectionConfiguration.put(name, set);
			}
			set.add(value);
		}
	}

	private static void authorize(URI baseUrl, String user, String pass) {
		URI uri = URI.create(baseUrl.toString() + "authenticate");
		String body = "";
		body += "loginName=" + Http.encode(user);
		body += "&loginPassword=" + Http.encode(pass);
		body += "&loginForm=Log+in";
		HttpRequest request = HttpRequest.newBuilder(uri).headers("Content-Type", "application/x-www-form-urlencoded")
				.POST(BodyPublishers.ofString(body)).build();
		try {
			HttpResponse<String> response = Http.getClient().send(request, BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				log.error(uri.toString());
				log.error("VIVO is not accessible. status code " + response.statusCode());
				exit();
			}
			if (response.body().contains("id=\"login\"")) {
				log.error("Authorization didn't succeed.");
				exitAndUsage();
			}

		} catch (Exception e) {
			log.error(e.getMessage());
			exit();
		}

	}

	public static String addPart(String name, String value) {
		return name + "=" + Http.encode(value);
	}

	private static String fixUrlSlash(String url) {
		if (!url.endsWith("/")) {
			url = url + "/";
		}
		return url;
	}

	private static void checkDataDir(File dataDir) {
		String path = dataDir.getAbsolutePath();
		if (!dataDir.exists()) {
			log.error(String.format("Directory %s doesn't exist.", path));
			exitAndUsage();
		}
		if (!dataDir.isDirectory()) {
			log.error(String.format("%s is not a directory.", path));
			exitAndUsage();
		}
		if (!dataDir.canWrite()) {
			log.error(String.format("%s is not writable.", path));
			exitAndUsage();
		}
	}

	public static void exitAndUsage() {
		printUsage();
		exit();
	}

	private static void exit() {
		System.exit(1);
	}

	private static void printUsage() {
		new HelpFormatter().printHelp("java -jar joai_connector.jar", options);
	}
}
