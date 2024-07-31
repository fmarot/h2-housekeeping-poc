package com.teamtter.h2.poc;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
	public static void main(String[] args) throws Exception {

		Path storageDir;
		if (args.length > 0) {
			// when run from command line, you can specify a DB path
			storageDir = Path.of(args[0]);
		} else {
			// when run from IDE
			storageDir = Paths.get(System.getProperty("java.io.tmpdir"), "h2poc");
		}

		Database db = new Database(storageDir);
		LOBCreator lobCreator = new LOBCreator(db);

		ExecutorService executorService = Executors.newFixedThreadPool(5);
		// This Semaphore prevents the deletor to work while the Producer is idle
		// in order to ensure the DB receives neither read nor write requests (and maybe
		// will be considered idle, who knows...)
		Semaphore semaphore = new Semaphore(1);

		Producer producer = new Producer(lobCreator, db, semaphore);
		executorService.submit(producer);

		LOBDeletorWithtargetSize lobDeletor = new LOBDeletorWithtargetSize(db, 30, 1, semaphore);
		Future<Void> futureDeletor = executorService.submit(lobDeletor);
		futureDeletor.get();	// just wait and stay in main thread

		// db.shutdown();
		// log.info("Waiting before exiting");
		// Thread.currentThread().sleep(30000);
	}

}