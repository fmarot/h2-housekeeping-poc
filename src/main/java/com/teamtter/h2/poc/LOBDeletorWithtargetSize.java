package com.teamtter.h2.poc;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

public class LOBDeletorWithtargetSize implements Callable<Void> {

	private Database	db;
	private int			targetSizeInGB;
	private Random		random	= new Random();
	private int			delayBetweenRunsInSeconds;
	private Semaphore	sema;

	public LOBDeletorWithtargetSize(Database db, int targetSizeInGB, int delayBetweenRunsInSeconds, Semaphore sema) {
		this.db = db;
		this.targetSizeInGB = targetSizeInGB;
		this.delayBetweenRunsInSeconds = delayBetweenRunsInSeconds;
		this.sema = sema;
	}

	@Override
	public Void call() throws Exception {

		while (true) {
			sema.acquire();
			List<Database.IdAndSize> modifiableDbEntries = db.selectIds();

			int currentSizeInMB = 0;
			while (currentSizeInMB < targetSizeInGB * 1024 && modifiableDbEntries.size() > 0) {
				// select a random entry
				int idToKeep = random.nextInt(modifiableDbEntries.size());
				// add its size
				currentSizeInMB += modifiableDbEntries.get(idToKeep).sizeInMB;
				// remove it from the list
				modifiableDbEntries.remove(idToKeep);
			}
			// delete all the entries still left in the List
			for (Database.IdAndSize entry : modifiableDbEntries) {
				db.deleteId(entry.id);
			}
			sema.release();
			//db.h2StoreCompactNow();
			db.displayInfos();

			Thread.sleep(delayBetweenRunsInSeconds * 1000);
		}
	}

}
