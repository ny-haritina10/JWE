package mg.jwe.orm.type;

import java.util.UUID;

public class UtilType {
    
    /**
     * Converts a given value to match a specified target type.
     * <p>
     * This method handles common type conversions such as converting between Number types,
     * String types, and UUIDs. It ensures that values are compatible with their intended fields
     * in an object model before setting them.
     * </p>
     *
     * @param value The value to convert to another type.
     * @param targetType The target class type that defines what type to convert to.
     * @return The converted value, or null if input was null.
     */
    public Object convertToFieldType(Object value, Class<?> targetType) {
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
}
