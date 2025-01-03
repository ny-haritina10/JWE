package mg.jwe.utils;

public class Formater {
    
    public String toCamelCase(String str) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(str.charAt(i)));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(str.charAt(i)));
                }
            }
        }
        
        return result.toString();
    }
    
    public String toPascalCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        // First convert to camel case to handle underscores
        String camelCase = toCamelCase(str);
        
        // Then capitalize the first letter
        return Character.toUpperCase(camelCase.charAt(0)) + camelCase.substring(1);
    }
}