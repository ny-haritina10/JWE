package mg.jwe.orm.query;

import java.util.List;
import java.util.StringJoiner;

public class UtilQuery {
    
    /**
     * Constructs an SQL INSERT statement for a specified table and columns.
     * <p>
     * This method builds a string representation of an INSERT SQL query using the provided
     * table name and a list of column names. It generates placeholders for the values to be
     * inserted, allowing for prepared statement execution.
     * </p>
     *
     * @param tableName The name of the table into which data will be inserted.
     * @param columns A list of column names corresponding to the values being inserted.
     * @return A string representing the constructed INSERT SQL statement.
     */
    public String buildInsertQuery(String tableName, List<String> columns) {
        StringJoiner columnJoiner = new StringJoiner(", ", "(", ")");
        StringJoiner valueJoiner = new StringJoiner(", ", "(", ")");
        
        for (String column : columns) {
            columnJoiner.add(column);
            valueJoiner.add("?");
        }

        return "INSERT INTO " + tableName + " " + columnJoiner.toString() + 
               " VALUES " + valueJoiner.toString();
    }

    /**
     * Constructs an SQL UPDATE statement for a specified table and columns.
     * <p>
     * This method builds a string representation of an UPDATE SQL query using the provided
     * table name, a list of columns to update, and the ID column to identify the record to be updated.
     * It generates placeholders for the values to be updated.
     * </p>
     *
     * @param tableName The name of the table where data will be updated.
     * @param columns A list of column names that need to be updated.
     * @param idColumn The name of the ID column used to identify which record to update.
     * @return A string representing the constructed UPDATE SQL statement.
     */
    public String buildUpdateQuery(String tableName, List<String> columns, String idColumn) {
        StringJoiner setJoiner = new StringJoiner(", ");
        for (String column : columns) {
            setJoiner.add(column + " = ?");
        }
        
        return "UPDATE " + tableName + " SET " + setJoiner.toString() + 
               " WHERE " + idColumn + " = ?";
    }
}