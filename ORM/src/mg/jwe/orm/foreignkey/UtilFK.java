package mg.jwe.orm.foreignkey;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import mg.jwe.orm.annotations.Column;
import mg.jwe.orm.annotations.ForeignKey;
import mg.jwe.orm.annotations.Id;
import mg.jwe.orm.annotations.Table;
import mg.jwe.orm.base.BaseModel;

@SuppressWarnings("unchecked")
public class UtilFK {

    /**
     * Loads foreign key relationships for a given instance from the database.
     * <p>
     * This method checks each field in the specified instance for foreign key annotations. If found,
     * it eagerly loads related objects by calling {@link #loadForeignKeyField(Connection, BaseModel, Field, ForeignKey)} 
     * if lazy loading is not specified in the annotation.
     * </p>
     *
     * @param connection The database connection used to retrieve foreign key data.
     * @param instance The instance whose foreign key relationships should be loaded.
     * @throws SQLException If a database access error occurs or this method is called on a closed connection.
     */
    public static <T extends BaseModel> void loadForeignKeys(Connection connection, T instance) 
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

    /**
     * Loads a foreign key field for a given instance from the database.
     * <p>
     * This method retrieves the foreign key value associated with the specified field and
     * sets the corresponding object in the instance. It constructs the foreign key column name
     * by appending "id_" to the field name and uses the provided connection to fetch the related object.
     * </p>
     *
     * @param connection The database connection used to retrieve foreign key data.
     * @param instance The instance whose foreign key relationship is being loaded.
     * @param field The field representing the foreign key in the instance.
     * @param fkAnnotation The ForeignKey annotation associated with the field.
     * @throws SQLException If a database access error occurs or this method is called on a closed connection.
     */
    public static <T extends BaseModel> void loadForeignKeyField(Connection connection, T instance, Field field, ForeignKey fkAnnotation) 
        throws SQLException 
    {
        try {
            String fkColumnName = "id_" + field.getName();
            Object fkValue = UtilFK.getForeignKeyValue(connection, instance, fkColumnName);
            
            if (fkValue != null) {
                Class<?> targetClass = field.getType();
                Object referencedObject = BaseModel.findById(connection, (Class<? extends BaseModel>) targetClass, fkValue);

                field.set(instance, referencedObject);
            }
        } 
        
        catch (IllegalAccessException e) 
        { throw new RuntimeException("Failed to load foreign key relationship", e); }
    }

    /**
     * Retrieves the ID of a given model instance.
     * <p>
     * This method scans the fields of the provided model for one annotated with {@link Id}.
     * It returns the value of that ID field, which is used for identifying instances in database operations.
     * </p>
     *
     * @param model The model instance from which to retrieve the ID value.
     * @return The ID value of the model instance, or null if no ID field is found.
     */
    public Object getForeignKeyId(BaseModel model) {
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


    /**
     * Retrieves the name of the ID column for a specified class.
     * <p>
     * This method scans through all fields in the class to find one annotated with {@link Id}.
     * If found, it returns its corresponding column name based on any {@link Column} annotation;
     * otherwise, it defaults to using the field name. If no ID field is found, an exception is thrown.
     * </p>
     *
     * @param clazz The class type from which to retrieve the ID column name.
     * @return The name of the ID column associated with this class.
     */
    public static String getIdColumnName(Class<?> clazz) {
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

    /**
     * Retrieves a foreign key value from a specified instance based on its ID in the database.
     * <p>
     * This method constructs a SELECT SQL statement to fetch a specific foreign key value using
     * its column name. It executes this query against the database using the provided connection
     * and returns the retrieved value.
     * </p>
     *
     * @param connection The database connection used to execute the query.
     * @param instance The instance whose foreign key value is being retrieved.
     * @param fkColumnName The name of the foreign key column to fetch from the database.
     * @return The foreign key value retrieved from the database, or null if not found or an error occurs.
     * @throws SQLException If a database access error occurs or this method is called on a closed connection.
     */
    public static Object getForeignKeyValue(Connection connection, BaseModel instance, String fkColumnName) 
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

    /**
     * Checks if a given field is annotated as a foreign key.
     *
     * @param field The field to check for foreign key annotation presence.
     * @return True if the field has a {@link ForeignKey} annotation; false otherwise.
     */
    public boolean isForeignKeyField(Field field) {
        return field.isAnnotationPresent(ForeignKey.class);
    }

    /**
     * Retrieves the foreign key column name for a specified field based on its annotation settings.
     *
     * @param field The field representing a foreign key relationship in an object model.
     * @return The name of the foreign key column as defined by its annotation; defaults to "id_" + field name if not specified explicitly in annotation settings.
     */
    public String getForeignKeyColumnName(Field field) {
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