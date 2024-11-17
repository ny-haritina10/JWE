package mg.jwe.orm.src;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

import mg.jwe.orm.annotations.Column;
import mg.jwe.orm.annotations.ForeignKey;
import mg.jwe.orm.annotations.Table;
import mg.jwe.orm.annotations.Id;

@SuppressWarnings("unchecked")
public abstract class BaseModel {

    public BaseModel()
    { }

    // INSERT METHODS
    public void save(Connection connection) throws SQLException {
        Class<?> clazz = this.getClass();

        Table tableAnnotation = clazz.getAnnotation(Table.class);
        String tableName = tableAnnotation.name();

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            Column columnAnnotation = field.getAnnotation(Column.class);
            ForeignKey fkAnnotation = field.getAnnotation(ForeignKey.class);
            
            if (columnAnnotation != null) {
                try {
                    Object value = field.get(this);
                    if (value != null) {
                        columns.add(columnAnnotation.name());
                        values.add(value);
                    }
                } 
                
                catch (IllegalAccessException e) 
                { throw new RuntimeException("Failed to access field", e); }
            } 
            
            // check for FK
            else if (fkAnnotation != null) {
                try {
                    Object value = field.get(this);
                    
                    if (value != null && value instanceof BaseModel) {
                        // get the ID of the referenced object
                        Object foreignId = getForeignKeyId((BaseModel) value);

                        if (foreignId != null) {
                            // Convention: foreign key column begins with id_
                            columns.add("id_" + field.getName()); 
                            values.add(foreignId);
                        }
                    }
                } 
                
                catch (IllegalAccessException e) 
                { throw new RuntimeException("Failed to access foreign key field", e); }
            }
        }

        String sql = buildInsertQuery(tableName, columns);
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.size(); i++) 
            { stmt.setObject(i + 1, values.get(i)); }

            stmt.executeUpdate();
            
            // Handle generated keys
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) 
                { setGeneratedId(generatedKeys.getObject(1)); }
            }
        }
    }

    // GET BY ID METHODS
    public static <T extends BaseModel> T findById(Connection connection, Class<T> clazz, Object id) 
        throws SQLException 
    {
        Table tableAnnotation = clazz.getAnnotation(Table.class);

        String tableName = tableAnnotation.name();
        String idColumn = getIdColumnName(clazz);

        String sql = "SELECT * FROM " + tableName + " WHERE " + idColumn + " = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                T instance = mapResultSetToObject(rs, clazz);
                loadForeignKeys(connection, instance);

                return instance;
            }
        }

        return null;
    }

    // UDPATE METHODS
    public void update(Connection connection) throws SQLException {
        Class<?> clazz = this.getClass();
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        String tableName = tableAnnotation.name();
        
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        Object idValue = null;
        String idColumn = null;

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Column columnAnnotation = field.getAnnotation(Column.class);

            if (columnAnnotation != null) {
                try {
                    Object value = field.get(this);
                    if (field.isAnnotationPresent(Id.class)) {
                        idColumn = columnAnnotation.name();
                        idValue = value;
                    } 
                    
                    else if (value != null) {
                        columns.add(columnAnnotation.name());
                        values.add(value);
                    }
                } 
                
                catch (IllegalAccessException e) 
                { throw new RuntimeException("Failed to access field", e); }
            }
        }

        String sql = buildUpdateQuery(tableName, columns, idColumn);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int paramIndex = 1;
            for (Object value : values) 
            { stmt.setObject(paramIndex++, value); }

            stmt.setObject(paramIndex, idValue);
            stmt.executeUpdate();
        }
    }

    // DELETE METHODS
    public void delete(Connection connection) throws SQLException {
        Class<?> clazz = this.getClass();

        Table tableAnnotation = clazz.getAnnotation(Table.class);
        String tableName = tableAnnotation.name();
        
        String idColumn = null;
        Object idValue = null;

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Id.class)) {
                Column columnAnnotation = field.getAnnotation(Column.class);
                idColumn = columnAnnotation.name();
                try {
                    idValue = field.get(this);
                    break;
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to access field", e);
                }
            }
        }

        String sql = "DELETE FROM " + tableName + " WHERE " + idColumn + " = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, idValue);
            stmt.executeUpdate();
        }
    }  

    private String buildInsertQuery(String tableName, List<String> columns) {
        StringJoiner columnJoiner = new StringJoiner(", ", "(", ")");
        StringJoiner valueJoiner = new StringJoiner(", ", "(", ")");
        
        for (String column : columns) {
            columnJoiner.add(column);
            valueJoiner.add("?");
        }

        return "INSERT INTO " + tableName + " " + columnJoiner.toString() + 
               " VALUES " + valueJoiner.toString();
    }

    private String buildUpdateQuery(String tableName, List<String> columns, String idColumn) {
        StringJoiner setJoiner = new StringJoiner(", ");
        for (String column : columns) {
            setJoiner.add(column + " = ?");
        }
        
        return "UPDATE " + tableName + " SET " + setJoiner.toString() + 
               " WHERE " + idColumn + " = ?";
    }

    private static <T extends BaseModel> T mapResultSetToObject(ResultSet rs, Class<T> clazz) {
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                Column columnAnnotation = field.getAnnotation(Column.class);
                if (columnAnnotation != null) {
                    String columnName = columnAnnotation.name();
                    Object value = rs.getObject(columnName);
                    field.set(instance, value);
                }
            }
            
            return instance;
        } 
        
        catch (Exception e) 
        { throw new RuntimeException("Failed to map ResultSet to object", e); }
    }
    
    private static <T extends BaseModel> void loadForeignKeys(Connection connection, T instance) 
        throws SQLException 
    {
        Class<?> clazz = instance.getClass();
        
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            ForeignKey fkAnnotation = field.getAnnotation(ForeignKey.class);
            
            if (fkAnnotation != null && !fkAnnotation.lazy()) {
                // Eager loading of foreign key relationship
                loadForeignKeyField(connection, instance, field, fkAnnotation);
            }
        }
    }

    private static <T extends BaseModel> void loadForeignKeyField(Connection connection, T instance, Field field, ForeignKey fkAnnotation) 
        throws SQLException 
    {
        try {
            String fkColumnName = "id_" + field.getName();
            Object fkValue = getForeignKeyValue(connection, instance, fkColumnName);
            
            if (fkValue != null) {
                Class<?> targetClass = field.getType();
                Object referencedObject = findById(connection, (Class<? extends BaseModel>) targetClass, fkValue);

                field.set(instance, referencedObject);
            }
        } 
        
        catch (IllegalAccessException e) 
        { throw new RuntimeException("Failed to load foreign key relationship", e); }
    }

    protected <T extends BaseModel> T loadForeignKeyLazy(Connection connection, String fieldName) 
        throws SQLException 
    {
        try {
            Field field = this.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            ForeignKey fkAnnotation = field.getAnnotation(ForeignKey.class);
            
            if (fkAnnotation != null && fkAnnotation.lazy()) {
                loadForeignKeyField(connection, this, field, fkAnnotation);
                return (T) field.get(this);
            }
        } 
        
        catch (NoSuchFieldException | IllegalAccessException e) 
        { throw new RuntimeException("Failed to lazy load foreign key relationship", e); }

        return null;
    }

    private Object getForeignKeyId(BaseModel model) {
        try {
            for (Field field : model.getClass().getDeclaredFields()) {
                field.setAccessible(true);

                if (field.isAnnotationPresent(Id.class)) 
                { return field.get(model); }
            }
            
        } 
        
        catch (IllegalAccessException e) 
        { throw new RuntimeException("Failed to get foreign key ID", e); }

        return null;
    }

    private void setGeneratedId(Object generatedId) {
        Class<?> clazz = this.getClass();
        
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Id.class)) {
                try {
                    // convert the generated ID to the appropriate type
                    Object convertedId = convertToFieldType(generatedId, field.getType());
                    field.set(this, convertedId);
                    return;
                } 
                
                catch (IllegalAccessException e) 
                { throw new RuntimeException("Failed to set generated ID", e); }
            }
        }
    }

    private static String getIdColumnName(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                Column columnAnnotation = field.getAnnotation(Column.class);
                if (columnAnnotation != null) {
                    return columnAnnotation.name();
                }
                // If no Column annotation is present, use the field name
                return field.getName();
            }
        }

        throw new RuntimeException("No ID field found in class " + clazz.getName());
    }

    private static Object getForeignKeyValue(Connection connection, BaseModel instance, String fkColumnName) 
        throws SQLException 
    {
        Class<?> clazz = instance.getClass();
        
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        String tableName = tableAnnotation.name();
        String idColumn = getIdColumnName(clazz);

        String sql = "SELECT " + fkColumnName + " FROM " + tableName + " WHERE " + idColumn + " = ?";
        
        try {
            // Find the ID value of the instance
            Object idValue = null;
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    idValue = field.get(instance);
                    break;
                }
            }

            if (idValue == null) 
            { return null; }

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, idValue);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) 
                { return rs.getObject(fkColumnName); }
            }
        } 
        
        catch (IllegalAccessException e) 
        { throw new RuntimeException("Failed to get foreign key value", e); }
        
        return null;
    }

    private Object convertToFieldType(Object value, Class<?> targetType) {
        if (value == null) 
        { return null; }

        // Handle common type conversions
        if (targetType == Long.class || targetType == long.class) {
            return ((Number) value).longValue();
        } 
        
        else if (targetType == Integer.class || targetType == int.class) 
        { return ((Number) value).intValue(); } 
        
        else if (targetType == String.class)
        { return value.toString(); } 
        
        else if (targetType == UUID.class && value instanceof String) 
        { return UUID.fromString((String) value); }

        return value;
    }

    private boolean isForeignKeyField(Field field) {
        return field.isAnnotationPresent(ForeignKey.class);
    }

    private String getForeignKeyColumnName(Field field) {
        ForeignKey fkAnnotation = field.getAnnotation(ForeignKey.class);
        if (fkAnnotation != null) {
            if (!fkAnnotation.column().isEmpty()) 
            { return fkAnnotation.column(); }

            // otherwise, use the convention: fieldName_id
            return "id_" + field.getName();
        }

        throw new RuntimeException("Field is not a foreign key: " + field.getName());
    }
}