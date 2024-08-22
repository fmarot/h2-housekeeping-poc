package com.teamtter.h2.poc;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class LOBCreator {

	private Database db;

	LOBCreator(Database db) {
		this.db = db;
	}

	public void insertRandomLob(int lengthInMB) {
		int lengthInBytes = lengthInMB * 1024 * 1024;
		byte[] blob = new byte[lengthInBytes];
		for (int i = 0; i < lengthInBytes; i++) {
			byte rand = (byte) ((int) (Math.random() * 255) - 128);
			blob[i] = rand;
		}
		try (ByteArrayInputStream bais = new ByteArrayInputStream(blob)) {
			db.insertBlob(lengthInMB, bais);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
