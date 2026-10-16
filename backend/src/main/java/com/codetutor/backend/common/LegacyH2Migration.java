package com.codetutor.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

/**
 * One-time, automatic copy of the accounts, projects and chat history from the old H2 file database
 * ({@code data/db/codetutor.mv.db}) into MySQL. It only runs when the application database is MySQL, the old
 * file exists and the MySQL tables are still empty, so it never overwrites anything. IDs are kept, so the
 * extracted projects and analysis files on disk still match. The H2 file is left untouched as a backup.
 */
@Component
public class LegacyH2Migration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyH2Migration.class);

    private final DataSource dataSource;
    private final String h2Path;
    private final String h2Password;

    public LegacyH2Migration(DataSource dataSource,
                             @Value("${legacy.h2-path:./data/db/codetutor}") String h2Path,
                             @Value("${legacy.h2-password:codetutor}") String h2Password) {
        this.dataSource = dataSource;
        this.h2Path = h2Path;
        this.h2Password = h2Password;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection target = dataSource.getConnection()) {
            String url = target.getMetaData().getURL();
            if (url == null || !url.startsWith("jdbc:mysql")) return;
            Path file = Paths.get(h2Path + ".mv.db").toAbsolutePath();
            if (!Files.exists(file)) return;
            if (count(target, "users") > 0 || count(target, "projects") > 0) return; // MySQL already has data

            String base = file.toString().replace('\\', '/').replaceAll("\\.mv\\.db$", "");
            log.info("Copying existing data from the old H2 database ({}) into MySQL…", file);
            try (Connection h2 = DriverManager.getConnection("jdbc:h2:file:" + base + ";IFEXISTS=TRUE", "sa", h2Password)) {
                target.setAutoCommit(false);
                try (Statement s = target.createStatement()) { s.execute("SET FOREIGN_KEY_CHECKS=0"); }
                Map<String, Integer> copied = new LinkedHashMap<>();
                for (String t : List.of("users", "projects", "chat_messages")) copied.put(t, copy(h2, target, t));
                try (Statement s = target.createStatement()) { s.execute("SET FOREIGN_KEY_CHECKS=1"); }
                target.commit();
                copied.forEach((t, n) -> log.info("  copied {} row(s) into {}", n, t));
            } catch (SQLException e) {
                target.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.warn("Could not copy the old H2 data into MySQL: {} (if the old backend is still running, stop it and restart this one)",
                    e.getMessage());
        }
    }

    private static long count(Connection c, String table) {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return r.next() ? r.getLong(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private static int copy(Connection h2, Connection my, String table) throws SQLException {
        List<String> myCols = new ArrayList<>();
        try (ResultSet rs = my.getMetaData().getColumns(my.getCatalog(), null, table, null)) {
            while (rs.next()) myCols.add(rs.getString("COLUMN_NAME"));
        }
        if (myCols.isEmpty()) throw new SQLException("MySQL table '" + table + "' does not exist");

        try (Statement st = h2.createStatement(); ResultSet r = st.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData md = r.getMetaData();
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 1; i <= md.getColumnCount(); i++) idx.put(md.getColumnName(i).toLowerCase(Locale.ROOT), i);
            List<String> cols = myCols.stream().filter(c -> idx.containsKey(c.toLowerCase(Locale.ROOT))).toList();
            String sql = "INSERT INTO " + table + " (" + String.join(",", cols.stream().map(c -> "`" + c + "`").toList())
                    + ") VALUES (" + String.join(",", Collections.nCopies(cols.size(), "?")) + ")";
            int n = 0;
            try (PreparedStatement ps = my.prepareStatement(sql)) {
                while (r.next()) {
                    for (int j = 0; j < cols.size(); j++) {
                        int i = idx.get(cols.get(j).toLowerCase(Locale.ROOT));
                        int type = md.getColumnType(i);
                        Object v = (type == Types.CLOB || type == Types.NCLOB) ? r.getString(i) : r.getObject(i);
                        ps.setObject(j + 1, v);
                    }
                    ps.addBatch();
                    n++;
                }
                ps.executeBatch();
            }
            return n;
        }
    }
}
