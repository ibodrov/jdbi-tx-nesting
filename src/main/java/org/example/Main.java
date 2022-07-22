package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        PostgreSQLContainer<?> pg = new PostgreSQLContainer("postgres:14");
        try {
            pg.start();

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(pg.getJdbcUrl());
            config.setUsername(pg.getUsername());
            config.setPassword(pg.getPassword());
            config.setAutoCommit(false); // the test will fail if autoCommit set to false here

            HikariDataSource ds = new HikariDataSource(config);

            Jdbi jdbi = Jdbi.create(ds);
            jdbi.installPlugin(new SqlObjectPlugin());

            jdbi.useTransaction(handle -> {
                handle.execute("CREATE TABLE test (x TEXT)");
            });

            test(jdbi);
        } finally {
            pg.stop();
        }
    }

    private static void test(Jdbi jdbi) {
        try {
            jdbi.useTransaction(handle -> {
                handle.attach(MyDao.class).insertRecord("foo");
                anotherMethod(jdbi);
                throw new RuntimeException("Make it roll back");
            });
        } catch (Exception e) {
            // ignore
        }

        List<String> records = jdbi.withExtension(MyDao.class, MyDao::listRecords);
        System.out.println("records: " + records);
    }

    private static void anotherMethod(Jdbi jdbi) {
        jdbi.useExtension(MyDao.class, dao -> dao.insertRecord("bar"));
    }

    interface MyDao {

        @SqlUpdate("INSERT INTO test (x) VALUES (?)")
        void insertRecord(String value);

        @SqlQuery("SELECT x FROM test")
        List<String> listRecords();
    }
}
