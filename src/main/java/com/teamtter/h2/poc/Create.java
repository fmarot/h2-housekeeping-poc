package com.teamtter.h2.poc;

import com.google.common.base.Stopwatch;
import lombok.AllArgsConstructor;

import java.sql.Connection;
import java.time.Duration;

@AllArgsConstructor
public class Create {
	private final Connection connection;

	public long createRecords(long desiredTotalSize) {
		Stopwatch stopwatch = Stopwatch.createStarted();

		// Do Stuff

		Duration duration = stopwatch.elapsed();
		return duration.getSeconds();
	}
}
