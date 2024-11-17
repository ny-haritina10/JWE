package mg.jwe.orm.mapper;

import java.lang.reflect.Field;
import java.sql.ResultSet;

import mg.jwe.orm.annotations.Column;
import mg.jwe.orm.annotations.Id;
import mg.jwe.orm.base.BaseModel;
import mg.jwe.orm.type.UtilType;

public class UtilMapper {
    
    /**
     * Maps a ResultSet to an instance of a specified class.
     * <p>
     * This method creates a new instance of the specified class and populates its fields
     * with values retrieved from the provided ResultSet. It uses the {@link Column} annotation
     * on each field to match ResultSet column names with object properties.
     * </p>
     *
     * @param rs The ResultSet containing data from a database query.
     * @param clazz The class type of the object to create and populate.
     * @return An instance of the specified class populated with data from the ResultSet.
     * @throws RuntimeException If there is an error during instantiation or field access.
     */
    public static <T extends BaseModel> T mapResultSetToObject(ResultSet rs, Class<T> clazz) {
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

    /**
     * Sets the generated ID for this instance after an insert operation.
     * <p>
     * This method identifies the ID field in the current class and sets its value based on
     * the generated ID returned from the database. It converts the generated ID to match
     * the appropriate type for that field.
     * </p>
     *
     * @param generatedId The generated ID value returned from an insert operation.
     */
    public void setGeneratedId(Object generatedId) {
        Class<?> clazz = this.getClass();
        
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Id.class)) {
                try {
                    // convert the generated ID to the appropriate type
                    Object convertedId = new UtilType().convertToFieldType(generatedId, field.getType());
                    field.set(this, convertedId);
                    return;
                } 
                
                catch (IllegalAccessException e) 
                { throw new RuntimeException("Failed to set generated ID", e); }
            }
        }
    }
}