package mg.jwe.utils;

public class TypeMapper {
    
    public String getJavaType(String sqlType) {
        switch (sqlType.toUpperCase()) {
            case "INTEGER":
            case "SERIAL":
                return "Integer";
            case "BIGINT":
            case "BIGSERIAL":
                return "Long";
            case "VARCHAR":
            case "TEXT":
                return "String";
            case "DATE":
                return "java.sql.Date";
            case "TIMESTAMP":
                return "java.sql.Timestamp";
            case "BOOLEAN":
                return "Boolean";
            case "DECIMAL":
            case "NUMERIC":
                return "java.math.BigDecimal";
            default:
                return "String";
        }
    }

    public String getInputType(String sqlType) {
        switch (sqlType.toUpperCase()) {
            case "INT":
            case "BIGINT":
            case "DECIMAL":
            case "NUMERIC":
                return "number";
            case "DATE":
                return "date";
            case "TIME":
                return "time";
            case "TIMESTAMP":
                return "datetime-local";
            default:
                return "text";
        }
    }

    public String getResultSetGetter(String sqlType) {
        switch (sqlType.toUpperCase()) {
            case "INTEGER":
            case "SERIAL":
                return "getInt";
            case "BIGINT":
            case "BIGSERIAL":
                return "getLong";
            case "VARCHAR":
            case "TEXT":
                return "getString";
            case "DATE":
                return "getDate";
            case "TIMESTAMP":
                return "getTimestamp";
            case "BOOLEAN":
                return "getBoolean";
            case "DECIMAL":
            case "NUMERIC":
                return "getBigDecimal";
            case "DOUBLE":
            case "FLOAT":
                return "getDouble";
            default:
                return "getString";
        }
    }
}