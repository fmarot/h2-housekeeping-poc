package com.teamtter.h2.poc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.h2.jdbcx.JdbcConnectionPool;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Database {

	private final Path			storageDir;
	private JdbcConnectionPool	pool;
	/* nbOfEntries is updated on each call to selectIds. it's not really realtime. */
	private int					nbOfEntries;

	@RequiredArgsConstructor
	@EqualsAndHashCode(onlyExplicitlyIncluded = true)
	public static class IdAndSize {
		@EqualsAndHashCode.Include
		public final long	id;
		public final int	sizeInMB;
	}

	public Database(Path storageDir) throws SQLException {
		this.storageDir = storageDir;
		storageDir.toFile().mkdirs();
		initPool();
		createDatabase();
	}

	private void initPool() {
		// DB_CLOSE_ON_EXIT=0 seems required because we close it manually with SHUTDOWN. Otherwise H2 creates a poc.trace.db containing an exception "Database is already closed"
		String url = "jdbc:h2:" + this.storageDir + "/poc" + ";RETENTION_TIME=0;DB_CLOSE_ON_EXIT=FALSE;MAX_COMPACT_TIME=20000;AUTO_COMPACT_FILL_RATE=70";
		pool = JdbcConnectionPool.create(url, "login", "pass");
	}

	/** debugging method which exports all files from the DB to the filesystem */
	public void reopenDBAndExportData() {
		initPool();
		String query = "SELECT * FROM the_table";
		try (PreparedStatement ps = getConnection().prepareStatement(query)) {
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				int id = rs.getInt("ID");
				int size = rs.getInt("BLOBLENGTH_MB");
				log.info("Will write file {} of size {}MB", id, size);
				try (InputStream stream = rs.getBinaryStream(3)) {
					FileUtils.copyInputStreamToFile(stream, new File(storageDir.toFile(), "" + id));
				}
			}
			rs = ps.executeQuery();
		} catch (Exception e) {
			log.error("", e);
		}
	}

	public Connection getConnection() throws SQLException {
		return pool.getConnection();
	}

	private void createDatabase() throws SQLException {
		String create = "CREATE TABLE IF NOT EXISTS the_table (ID bigint auto_increment, BLOBLENGTH_MB INTEGER, DATA BLOB)";
		try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
			int result = stmt.executeUpdate(create);
			log.info("Table created: {}", result);
		}
	}

	public void insertBlob(int streamLength, ByteArrayInputStream blobStream) {
		String statement = "INSERT INTO the_table (BLOBLENGTH_MB, DATA) VALUES (?, ?)";
		try (Connection conn = getConnection(); PreparedStatement prepStmt = conn.prepareStatement(statement)) {
			prepStmt.setInt(1, streamLength);
			prepStmt.setBinaryStream(2, blobStream);
			int result = prepStmt.executeUpdate();
			log.info("Record created: {}MB", streamLength);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void deleteId(long idToDelete) throws SQLException {

		log.info("Going to delete id {}", idToDelete);

		String deleteStmt = "DELETE FROM the_table WHERE ID = ?";
		try (Connection conn = getConnection();
				PreparedStatement prepStmt = conn.prepareStatement(deleteStmt)) {
			prepStmt.setLong(1, idToDelete);
			int nbRowsDeleted = prepStmt.executeUpdate();
			log.debug("nb rows deleted: {}", nbRowsDeleted);
		}
	}

	public List<IdAndSize> selectIds() throws SQLException {
		try (Connection conn = getConnection()) {
			List<IdAndSize> ids = new ArrayList<>();
			String selectStmt = "Select ID, BLOBLENGTH_MB from the_table ORDER BY ID";
			try (ResultSet resultSet = conn.createStatement().executeQuery(selectStmt)) {
				while (resultSet.next()) {
					Long id = resultSet.getLong("ID");
					int size = resultSet.getInt("BLOBLENGTH_MB");
					IdAndSize value = new IdAndSize(id, size);
					ids.add(value);
				}
			}
			nbOfEntries = ids.size();
			//log.info("The DB contains {} records, for a total of {}MB - fileSize:  {}MB", ids.size(), dbSize, computeDBSizeOnFilesystemInMB());
			return ids;
		}
	}

	long computeDBSizeOnFilesystemInMB() {
		return FileUtils.sizeOfDirectory(storageDir.toFile()) / 1024 / 1024;
	}

	public void shutdown() {
		String statement = "SHUTDOWN COMPACT";
		try (Connection conn = getConnection();) {
			displayInfos();
			log.info("Shutting down");
			int result = conn.createStatement().executeUpdate(statement);
			log.info("Shut down");
			log.info("Final size on the disk after shutdown defrag: {}MB", computeDBSizeOnFilesystemInMB());
			reopenDBAndExportData();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private long sizeOfPayloadInMB() {
		long sum = -1;
		try {
			List<IdAndSize> entries = selectIds();
			sum = entries.stream().mapToLong(it -> it.sizeInMB).sum();
		} catch (SQLException e) {
			log.error("", e);
		}
		return sum;
	}

	public void displayInfos() {
		log.info("Content of DB: {} entries for {} MB - DB size: {}MB", nbOfEntries, sizeOfPayloadInMB(), computeDBSizeOnFilesystemInMB());
	}

	public boolean DBfileIsTwiceLargerThanPayload() {
		long fileSizeInMB = computeDBSizeOnFilesystemInMB();
		long payloadSizeInMB = sizeOfPayloadInMB();
		boolean atLeastTwiceLarger = payloadSizeInMB * 2 < fileSizeInMB;
		return atLeastTwiceLarger;
	}

	/** Note: there are 2 ways to 'shrink' the DB file:
	 * - calling store.compactFile (we do it regularly here in the TimerTask)
	 * - SHUTDOWN COMPACT; (we do it when closing the DB)
	 * It seems like calling the latter while doing other work on the DB is not a good idea at all, while
	 * the former seems ok to be called at normal runtime. */
	/*public void h2StoreCompactNow() {
		try (JdbcConnection conn = (JdbcConnection) getConnection()) {
			SessionLocal sessionLocal = (SessionLocal) conn.getSession();
			Store store = sessionLocal.getDatabase().getStore();
			// log.info("Starting DB compact...");
			store.compactFile(2000);
			// log.info("DB compact done.");
		} catch (Exception e) {
			log.error("", e);
		}
	}*/
}
