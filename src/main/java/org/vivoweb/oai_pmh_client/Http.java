package org.vivoweb.oai_pmh_client;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http {
	private static Logger log = LoggerFactory.getLogger(Http.class);

	private static HttpClient httpClient;
	
	public static void init() {
		httpClient = HttpClient.newBuilder()
			.version(Version.HTTP_2)
			.followRedirects(Redirect.ALWAYS)
			.connectTimeout(Duration.ofSeconds(3))
			.build();
	}
	
	public static HttpClient getClient() {
		return httpClient;
	}

	static String encode(String input) {
		try {
			input = URLEncoder.encode(input, StandardCharsets.UTF_8.toString()).replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			log.error(e.getMessage());
		}
		return input;
	}

}
