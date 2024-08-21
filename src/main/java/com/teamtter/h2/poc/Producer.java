package com.teamtter.h2.poc;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Producer implements Callable<Void> {

	private Random		rand	= new Random();
	private LOBCreator	lobCreator;
	private Database	db;
	private Semaphore	sema;

	public Producer(LOBCreator lobCreator, Database db, Semaphore sema) {
		this.lobCreator = lobCreator;
		this.db = db;
		this.sema = sema;
	}

	@Override
	public Void call() throws Exception {

		while (true) {
			int lobSize = rand.nextInt(1000); // lob up to 1Go
			lobCreator.insertRandomLob(lobSize);

			if (db.DBfileIsTwiceLargerThanPayload()) {
				sema.acquire();
				// wait a bit to try to trigger idle H2 and housekeeping
				int sleepDurationInMinutes = 6;
				log.info("DBfileIsTwiceLargerThanPayload => will wait {} minutes", sleepDurationInMinutes);
				int sleepDurationInMs = sleepDurationInMinutes * 60 * 1000;
				Thread.sleep(sleepDurationInMs);
				log.info("DBfileIsTwiceLargerThanPayload => DONE wait {} minutes", sleepDurationInMinutes);
				//sema.release();

				db.displayInfos();
				Thread.sleep(5_000);
				db.shutdown();
				log.info("Will exit now...");
				System.exit(0);	// EXIT PROGRAMM
			} else {
				// wait a bit
				int sleepFor = rand.nextInt(400); // sleep a bit, in ms
				Thread.sleep(sleepFor);
			}
		}
	}

}
