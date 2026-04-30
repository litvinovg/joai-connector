/* $This file is distributed under the terms of the license in LICENSE$ */

package org.vivoweb.joai_connector;

import java.io.File;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Storage {
	private static DB db;
	private static HTreeMap<String, String> records;
	private static Logger log = LoggerFactory.getLogger(Storage.class);

	static {
		try {
		init();
		} catch(org.mapdb.DBException e) {
			e.printStackTrace();
			log.error("MapsDB volume looks to be corrupted. Removing the file and initializaing the volume.");
			File dbFile = new File("file.db");
			if (dbFile.exists()) {
				dbFile.delete();	
			}
			init();
		}
	}

	private static void init() {
		db = DBMaker.fileDB("file.db").closeOnJvmShutdown().make();
		records = db.hashMap("records") .keySerializer(Serializer.STRING)
			    .valueSerializer(Serializer.STRING).createOrOpen();
	}

	public static void add(String key, String value) {
		records.put(key, value);
	}
	
	public static String get(String key) {
		return records.get(key);
	}
	
	public static void remove(String key) {
		records.remove(key);
	}

}
