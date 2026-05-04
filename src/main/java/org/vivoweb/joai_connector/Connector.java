/* $This file is distributed under the terms of the license in LICENSE$ */

package org.vivoweb.joai_connector;

import java.io.File;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;

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
	private static Options options = new Options();
	private static Logger log = LoggerFactory.getLogger(Connector.class);
	private static final String apiSuffix = "api/dataRequest/";
	static final String apiConfigSuffix = "OAI PMH sets configuration";
    private static final ObjectMapper mapper = new ObjectMapper();

	public static void main(String[] args) {
		Option URL_OPTION = new Option("url", true, "Vitro/VIVO instance URL");
		Option DATA_OPTION = new Option("data", true, "Path to data directory");
		Option USER_OPTION = new Option("user", true, "Vitro/VIVO user email");
		Option PASS_OPTION = new Option("pass", true, "Vitro/VIVO password");
		options.addOption(URL_OPTION);
		options.addOption(DATA_OPTION);
		options.addOption(USER_OPTION);
		options.addOption(PASS_OPTION);
		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cmdline = parser.parse(options, args);
			String urlInput = URL_OPTION.getOpt();
			String dataPath = DATA_OPTION.getOpt();
			String user = USER_OPTION.getOpt();
			String pass = PASS_OPTION.getOpt();
			if (!cmdline.hasOption(urlInput) || !cmdline.hasOption(urlInput)) {
				exitAndUsage();
			}
			File dataDir = new File(cmdline.getOptionValue(dataPath));
			URI baseUrl = URI.create(fixUrlSlash(cmdline, urlInput));
			Http.init();
			checkDataDir(dataDir);
			if (cmdline.hasOption(user) && cmdline.hasOption(pass)) {
				authorize(baseUrl, cmdline.getOptionValue(user), cmdline.getOptionValue(pass));
			}
			new DataSetProcessor(dataDir, baseUrl, 100).processAll(getConfiguration(baseUrl));
		} catch (ParseException e) {
			log.error(e.getMessage());
		}
	}

	private static Map<String,Map<String,String>> getConfiguration(URI url) {
		String configurationUrl = url.toString() + apiSuffix + Http.encode(apiConfigSuffix);
		URI configUri = URI.create(configurationUrl);
		HttpRequest request = HttpRequest.newBuilder(configUri).GET().build();
		Map<String,Map<String,String>> configuration = new HashMap<>();
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

	private static void processConfigurationItem(Map<String, Map<String, String>> configuration, JsonNode data) {
		ObjectNode config = data.asObject();
		Map<String, String> settings = new HashMap<>();
		for (String name : config.propertyNames()) {
			String value = config.at("/" + name + "/value").asString();
			if (name.equals("directory")) {
				configuration.put(value, settings);
			} else {
				settings.put(name, value);
			}
		}
	}

	private static void authorize(URI baseUrl, String user, String pass) {
		URI uri = URI.create(baseUrl.toString() + "authenticate");
        String body = "";
        body += "loginName=" + Http.encode(user);
        body += "&loginPassword=" + Http.encode(pass);
        body += "&loginForm=Log+in";
		HttpRequest request = HttpRequest
				.newBuilder(uri)
                .headers("Content-Type", "application/x-www-form-urlencoded")
				.POST(BodyPublishers.ofString(body))
				.build();
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
	
	private static String fixUrlSlash(CommandLine cmdline, String urlInput) {
		String url = cmdline.getOptionValue(urlInput);
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
