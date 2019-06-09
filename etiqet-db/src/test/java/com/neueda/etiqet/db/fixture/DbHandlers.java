package com.neueda.etiqet.db.fixture;

import com.jcraft.jsch.Session;

import static org.jooq.impl.DSL.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jooq.*;
import org.jooq.impl.DSL;

import java.sql.*;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

public class DbHandlers {

    public static final String DEFAULT_SERVER_ALIAS = "default";

    private static Session session;
    private static Connection conn;
    private static DSLContext dslContext;

    private static Result<Record> results;
    private static HashMap<String, SelectQuery<Record>> queries;

    static {
        queries = new HashMap<>();
    }

    public static void connect(String serverAlias) {
        DbServer dbServer = DbBase.getServerConfig(serverAlias);
        if (dbServer.getSshTunnel() != null) {
            createTunnel(dbServer.getSshTunnel());
        }
        connect(dbServer);
    }

    public static void connect(DbServer dbServer) {
        try {
            initSqlDriver(dbServer.getDriverClass());
            String url = "";
            if (dbServer.getDialect() == SQLDialect.POSTGRES) {
                url = buildUrlPostgres(dbServer.getSubprotocol(), dbServer.getHost(), dbServer.getDbName(),
                    dbServer.getPort());
            }
            conn = DriverManager.getConnection(url, dbServer.getUser(), dbServer.getPassword());
            dslContext = DSL.using(conn, dbServer.getDialect());
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void initSqlDriver(String driverClass) throws ClassNotFoundException {
        Class.forName(driverClass);
    }

    public static String buildUrlPostgres(String subprotocol, String host, String dbName, Integer port) {
        StringBuilder url = new StringBuilder();
        url.append(subprotocol);
        url.append(host);
        url.append(":");
        url.append(port);
        url.append("/");
        url.append(dbName);
        return url.toString();
    }

    // todo
    public static void buildUrlSqlServer(String subprotocol, String serverName, String instanceName,
                                    Integer port, String[] properties) {
        StringBuilder url = new StringBuilder();
        url.append(subprotocol);
        if (serverName != null) {
            url.append(serverName);
        }
        if (instanceName != null) {
            url.append("\\");
            url.append(instanceName);
        }
        if (port != null) {
            url.append(":");
            url.append(port.toString());
        }
        if (properties.length > 0) {
            url.append(String.join(";", properties));
        }
    }

    public static void createTunnel(SshTunnel sshTunnel) {

    }

    /**QUERIES*/

    public static void selectAll(String tableName, String distinctColumnNames) {
        Name table = DSL.name(tableName);
        if (distinctColumnNames != null) {
            ArrayList<Field<Object>> distinctColumns = DbUtils.resolveToFieldList(distinctColumnNames);
            results = dslContext.select().distinctOn(distinctColumns).from(table).fetch();
        }
        else {
            results = dslContext.select().from(table).fetch();
        }
    }

    public static void selectAllWithCondition(String tableName, String conditionExp, String distinctColumnNames) {
        Name table = DSL.name(tableName);
        Condition condition = DSL.condition(conditionExp);
        if (distinctColumnNames != null) {
            ArrayList<Field<Object>> distinctColumns = DbUtils.resolveToFieldList(distinctColumnNames);
            results = dslContext.select().distinctOn(distinctColumns).from(table).where(condition).fetch();
        }
        else {
            results = dslContext.select().from(table).where(condition).fetch();
        }
    }

    public static void selectColumns(ArrayList<String> columnNames, String tableName, String distinctColumnNames) {
        Name table = DSL.name(tableName);
        ArrayList<Field<Object>> columns = DbUtils.resolveToFieldList(columnNames);
        if (distinctColumnNames != null) {
            ArrayList<Field<Object>> distinctColumns = DbUtils.resolveToFieldList(distinctColumnNames);
            results = dslContext.select(columns).distinctOn(distinctColumns).from(table).fetch();
        }
        else {
            results = dslContext.select(columns).from(table).fetch();
        }
    }

    public static void selectColumnsWithCondition(ArrayList<String> columnNames, String tableName, String conditionExp, String distinctColumnNames) {
        Name table = DSL.name(tableName);
        Condition condition = DSL.condition(conditionExp);

        ArrayList<Field<Object>> columns = DbUtils.resolveToFieldList(columnNames);
        if (distinctColumnNames != null) {
            ArrayList<Field<Object>> distinctColumns = DbUtils.resolveToFieldList(distinctColumnNames);
            results = dslContext.select(columns).distinctOn(distinctColumns).from(table).where(condition).fetch();
        }
        else {
            results = dslContext.select(columns).from(table).where(condition).fetch();
        }
    }

    public static void sendRawSQLQuery(String query) {
        results = dslContext.fetch(query);
    }

    /**QUERY BUILDER*/

    public static void initQueryBuilder(String alias) {
        queries.put(alias, dslContext.selectQuery());
    }

    public static void addSelect(String alias) {
        queries.get(alias).addSelect();
    }

    public static void addSelect(ArrayList<String> columnNames,  String alias) {
        ArrayList<Field<Object>> columns = new ArrayList<>();
        for (String columnName : columnNames) {
            columns.add(field(DSL.name(columnName)));
        }
        queries.get(alias).addSelect(columns);
    }

    public static void addFrom(String tableName, String alias) {
        Table<Record> table = DSL.table(tableName);
        queries.get(alias).addFrom(table);
    }

    public static void addCondition(String conditionExp, String alias) {
        Condition condition = DSL.condition(conditionExp);
        queries.get(alias).addConditions(condition);
    }

    public static void addGroupBy(ArrayList<String> columnNames, String alias) {
        queries.get(alias).addGroupBy(DbUtils.resolveToFieldList(columnNames));
    }

    public static void addHaving(String conditionExp, String alias) {
        Condition condition = DSL.condition(conditionExp);
        queries.get(alias).addHaving(condition);
    }

    public static void addDistinctOn(ArrayList<String> distinctColumnNames, String alias) {
        queries.get(alias).addDistinctOn(DbUtils.resolveToFieldList(distinctColumnNames));
    }

    public static void addOrderBy(ArrayList<String> columnNames, String alias) {
        queries.get(alias).addOrderBy(DbUtils.resolveToFieldList(columnNames));
    }

    public static void addJoin(String tableName, JoinType joinType, String alias) {
        Table<Record> table = DSL.table(tableName);
        queries.get(alias).addJoin(table, joinType);
    }

    public static void addJoinWithCondition(String tableName, JoinType joinType, String conditionExp,  String alias) {
        Table<Record> table = DSL.table(tableName);
        Condition condition = DSL.condition(conditionExp);
        queries.get(alias).addJoin(table, joinType, condition);
    }

    public static void executeQuery(String alias) {
        results = queries.get(alias).fetch();
    }

    /**UPDATE*/

    public static void updateWithCondition(String tableName, String newFieldValueParams, String conditionExp) {
        Table<Record> table = DSL.table(tableName);
        Condition condition = DSL.condition(conditionExp);
        HashMap<String, String> newFieldValues = DbUtils.resolveParamsToFieldValMap(newFieldValueParams);
        HashMap<Field<Object>, Object> typeResolvedFieldValues = DbUtils.resolveToFieldValMap(
            dslContext, tableName, newFieldValues);
        dslContext.update(table).set(typeResolvedFieldValues).where(condition).execute();
    }

    public static void insertInto(ArrayList<String> values, String tableName) {
        Table<Record> table = DSL.table(tableName);
        dslContext.insertInto(table).values(DbUtils.resolveToFieldList(values));
    }

    public static void insertInto(ArrayList<String> values, ArrayList<String> columnNames, String tableName) {
        Table<Record> table = DSL.table(tableName);
        dslContext.insertInto(table).columns(DbUtils.resolveToFieldList(columnNames)).values(DbUtils.resolveToFieldList(values));
    }

    public static void deleteAll(String tableName) {
        Table<Record> table = DSL.table(tableName);
        dslContext.delete(table).execute();
    }

    public static void deleteWithCondition(String tableName, String conditionExp) {
        Table<Record> table = DSL.table(tableName);
        Condition condition = DSL.condition(conditionExp);
        dslContext.delete(table).where(condition);
    }

    /**FILTERS*/

    // todo - a method of saving values
    public static void getColumnValAtRow(Integer rowIndex, String column, String alias) {
        results.getValue(rowIndex, column);
    }

    /**VALIDATIONS*/

    public static void checkRowCountGreaterThan(int expectedRows) {
        assertTrue(expectedRows > results.size());
    }

    public static void checkRowCountLessThan(int expectedRows) {
        assertTrue(expectedRows < results.size());
    }

    public static void checkRowCountEqualTo(int expectedRows) {
        assertEquals(expectedRows, results.size());
    }

    public static void checkValueForColumnAtRow(String value, int rowIndex, String columnName) {
        assertEquals(value, results.get(rowIndex).get(field(DSL.name(columnName))));
    }

    public static void checkValueForColumnAtRowContains(String value, int rowIndex, String columnName) {
        assertTrue(results.get(rowIndex).get(field(DSL.name(columnName))).toString().contains(value));
    }

    public static void checkValuesAcrossRows(ArrayList<String> values, String columnName) {
        for (int i = 0; i < results.size(); i++) {
            assertEquals(values.get(i), results.getValue(i, field(DSL.name(columnName))));
        }
    }

    public static void checkValuesAcrossColumns(ArrayList<String> values, int rowIndex) {
        Record row = results.get(rowIndex);
        for (int i = 0; i < row.size(); i++) {
            assertEquals(values.get(i), row.get(i));
        }
    }

    /**UTILS*/

    public static void printResults() {
        if (results != null) {
            System.out.println(results);
        }
    }
}
