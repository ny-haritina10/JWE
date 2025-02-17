package mg.jwe.orm.base;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;


import mg.jwe.orm.annotations.Column;
import mg.jwe.orm.annotations.ForeignKey;
import mg.jwe.orm.annotations.Id;
import mg.jwe.orm.annotations.Table;
import mg.jwe.orm.criteria.Criterion;
import mg.jwe.orm.foreignkey.UtilFK;
import mg.jwe.orm.mapper.UtilMapper;
import mg.jwe.orm.query.UtilQuery;

@SuppressWarnings("unchecked")
public abstract class BaseModel {

    public BaseModel()
    { }

    /**
     * Saves the current instance of the class to the database.
     * <p>
     * This method constructs an INSERT SQL statement based on the annotations present on the class fields.
     * It retrieves the table name from the {@link Table} annotation and the column names and values from
     * the {@link Column} and {@link ForeignKey} annotations. The method also handles foreign key relationships
     * by retrieving the ID of referenced objects.
     * </p>
     *
     * @param connection The database connection to use for executing the insert operation.
     * @throws SQLException If a database access error occurs or this method is called on a closed connection.
     */
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
                        Object foreignId = new UtilFK().getForeignKeyId((BaseModel) value);

                        if (foreignId != null) {
                            // Convention: foreign key column begins with id_
                            columns.add(UtilFK.camelToSnake("id_" + field.getName())); 
                            values.add(foreignId);
                        }
                    }
                } 
                
                catch (IllegalAccessException e) 
                { throw new RuntimeException("Failed to access foreign key field", e); }
            }
        }

        String sql = new UtilQuery().buildInsertQuery(tableName, columns);
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.size(); i++) 
            { stmt.setObject(i + 1, values.get(i)); }

            System.out.println("SQL from save: " + sql);
            stmt.executeUpdate();
            
            // Handle generated keys
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) 
                { new UtilMapper().setGeneratedId(generatedKeys.getObject(1)); }
            }
        }
    }

    /**
     * Updates the current instance of the class in the database.
     * <p>
     * This method constructs an UPDATE SQL statement based on the annotations present on
     * the class fields. It identifies which fields to update and uses the {@link Table}
     * annotation to determine the table name. The method also identifies the ID column
     * to specify which record should be updated, and handles foreign key relationships
     * by retrieving the ID of referenced objects.
     * </p>
     *
     * @param connection The database connection to use for executing the update operation.
     * @throws SQLException If a database access error occurs or this method is called on a closed connection.
     */
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
            ForeignKey fkAnnotation = field.getAnnotation(ForeignKey.class);

            try {
                Object value = field.get(this);
                if (columnAnnotation != null) {
                    if (field.isAnnotationPresent(Id.class)) {
                        idColumn = columnAnnotation.name();
                        idValue = value;
                    } else if (value != null) {
                        columns.add(columnAnnotation.name());
                        values.add(value);
                    }
                } else if (fkAnnotation != null && value instanceof BaseModel) {
                    // Handle foreign key
                    Object foreignId = new UtilFK().getForeignKeyId((BaseModel) value);
                    if (foreignId != null) {
                        columns.add("id_" + field.getName());
                        values.add(foreignId);
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access field", e);
            }
        }

        String sql = new UtilQuery().buildUpdateQuery(tableName, columns, idColumn);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int paramIndex = 1;
            for (Object value : values) {
                stmt.setObject(paramIndex++, value);
            }
            stmt.setObject(paramIndex, idValue);
            stmt.executeUpdate();
        }

        System.out.println("Update query: " + sql);
    }


    /**
     * Deletes the current instance of the class from the database.
     * <p>
     * This method constructs a DELETE SQL statement based on the annotations present on
     * the class fields. It identifies which record to delete using the ID column specified
     * by the {@link Id} annotation and retrieves its value.
     * </p>
     *
     * @param connection The database connection to use for executing the delete operation.
     * @throws SQLException If a database access error occurs or this method is called on a closed connection.
     */
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

    /**
     * Retrieves all records for a given entity type
     * @param <T> The entity type
     * @param clazz The class of the entity
     * @return An array of all entities
     * @throws SQLException if a database error occurs
     */
    public static <T extends BaseModel> T[] getAll(Connection connection, Class<T> clazz) 
        throws SQLException 
    {
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        if (tableAnnotation == null) 
        { throw new RuntimeException("No Table annotation found for class " + clazz.getName()); }
        
        String tableName = tableAnnotation.name();
        List<T> results = new ArrayList<>();
        
        String sql = "SELECT * FROM " + tableName + " ORDER BY id";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                T instance = UtilMapper.mapResultSetToObject(rs, clazz);
                UtilFK.loadForeignKeys(connection, instance);
                results.add(instance);
            }
        }
        
        T[] array = (T[]) java.lang.reflect.Array.newInstance(clazz, results.size());
        return results.toArray(array);
    }

    /**
     * Retrieves an instance of a specified class from the database based on its ID.
     * <p>
     * This method constructs a SELECT SQL statement to find a record in the database
     * that matches the provided ID. It uses the {@link Table} annotation to determine
     * the table name and the ID column name, and maps the result set to an instance of
     * the specified class.
     * </p>
     *
     * @param connection The database connection to use for executing the query.
     * @param clazz The class type of the object to retrieve, which must extend {@link BaseModel}.
     * @param id The ID of the object to find in the database.
     * @return An instance of the specified class populated with data from the database, or null if no record is found.
     * @throws SQLException If a database access error occurs or this method is called on a closed connection.
     */
    public static <T extends BaseModel> T findById(Connection connection, Class<T> clazz, Object id) 
        throws SQLException 
    {
        Table tableAnnotation = clazz.getAnnotation(Table.class);

        String tableName = tableAnnotation.name();
        String idColumn = UtilFK.getIdColumnName(clazz);

        String sql = "SELECT * FROM " + tableName + " WHERE " + idColumn + " = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                T instance = UtilMapper.mapResultSetToObject(rs, clazz);
                UtilFK.loadForeignKeys(connection, instance);

                return instance;
            }
        }

        return null;
    }

    /**
     * Retrieves the last inserted record based on auto-incrementing ID
     * Note: This assumes the ID is auto-incrementing and the highest ID is the last inserted
     * 
     * @param <T> The entity type
     * @param clazz The class of the entity
     * @return The last inserted instance or null if table is empty
     * @throws SQLException if a database error occurs
     */
    public static <T extends BaseModel> T getLastInserted(Connection connection, Class<T> clazz) 
        throws SQLException 
    {
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        if (tableAnnotation == null) {
            throw new RuntimeException("No Table annotation found for class " + clazz.getName());
        }
        
        String tableName = tableAnnotation.name();
        String idColumn = UtilFK.getIdColumnName(clazz);
        
        String sql = "SELECT * FROM " + tableName + " ORDER BY " + idColumn + " DESC LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                T instance = UtilMapper.mapResultSetToObject(rs, clazz);
                UtilFK.loadForeignKeys(connection, instance);
                return instance;
            }
        }
        
        return null;
    }

    /**
     * Finds records matching all specified criteria (AND condition)
     * Example usage:
     * Product.findByCriteria(connection, Product.class,
     *     new Criterion("name", "=", "iPhone"),
     *     new Criterion("price", ">=", 1200)
     * );
     *
     * @param connection Database connection
     * @param clazz The entity class
     * @param criteria Variable number of criteria to match
     * @return Array of matching entities
     * @throws SQLException If a database error occurs
     */
    public static <T extends BaseModel> T[] findByCriteria(Connection connection, Class<T> clazz, Criterion... criteria) 
        throws SQLException 
    {
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        if (tableAnnotation == null) {
            throw new RuntimeException("No Table annotation found for class " + clazz.getName());
        }
        
        String tableName = tableAnnotation.name();
        List<T> results = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder("SELECT * FROM " + tableName);
        
        if (criteria.length > 0) {
            sql.append(" WHERE ");
            for (int i = 0; i < criteria.length; i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }
                sql.append(criteria[i].getColumn())
                   .append(" ")
                   .append(criteria[i].getOperator())
                   .append(" ?");
            }
        }
        
        sql.append(" ORDER BY id");
        
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            // Set parameters
            for (int i = 0; i < criteria.length; i++) {
                stmt.setObject(i + 1, criteria[i].getValue());
            }
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                T instance = UtilMapper.mapResultSetToObject(rs, clazz);
                UtilFK.loadForeignKeys(connection, instance);
                results.add(instance);
            }
        }
        
        T[] array = (T[]) java.lang.reflect.Array.newInstance(clazz, results.size());
        return results.toArray(array);
    }

    /**
     * Finds records matching any of the specified criteria (OR condition).
     * Example usage:
     * Product.findByAnyCriteria(connection, Product.class,
     *     new Criterion("name", "LIKE", "%iPhone%"),
     *     new Criterion("price", "<", 1000)
     * );
     *
     * @param connection Database connection
     * @param clazz The entity class
     * @param criteria Variable number of criteria to match
     * @return Array of matching entities
     * @throws SQLException If a database error occurs
     */
    public static <T extends BaseModel> T[] findByAnyCriteria(Connection connection, Class<T> clazz, Criterion... criteria) 
        throws SQLException 
    {
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        if (tableAnnotation == null) {
            throw new RuntimeException("No Table annotation found for class " + clazz.getName());
        }
        
        String tableName = tableAnnotation.name();
        List<T> results = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder("SELECT * FROM " + tableName);
        
        if (criteria.length > 0) {
            sql.append(" WHERE ");
            for (int i = 0; i < criteria.length; i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                sql.append(criteria[i].getColumn())
                   .append(" ")
                   .append(criteria[i].getOperator())
                   .append(" ?");
            }
        }
        
        sql.append(" ORDER BY id");
        
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            // Set parameters
            for (int i = 0; i < criteria.length; i++) {
                stmt.setObject(i + 1, criteria[i].getValue());
            }
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                T instance = UtilMapper.mapResultSetToObject(rs, clazz);
                UtilFK.loadForeignKeys(connection, instance);
                results.add(instance);
            }
        }
        
        T[] array = (T[]) java.lang.reflect.Array.newInstance(clazz, results.size());
        return results.toArray(array);
    }
}