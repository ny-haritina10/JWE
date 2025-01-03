package mg.jwe.codegen;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import mg.jwe.utils.ForeignKeyInfo;
import mg.jwe.utils.Formater;
import mg.jwe.utils.TypeMapper;

import java.util.Arrays;

public class ModelCodeGenerator {

    private Formater formater;
    private TypeMapper type;

    public ModelCodeGenerator() {
        this.formater = new Formater();
        this.type = new TypeMapper();
    }
    
    public void generateModelForTable(Connection connection, String tableName, String outputPath) 
        throws SQLException, IOException 
    {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet columns = metaData.getColumns(null, null, tableName, null);
        ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, tableName);
        ResultSet foreignKeys = metaData.getImportedKeys(null, null, tableName);
        
        // Generate class name (convert to PascalCase)
        String className = Arrays.stream(tableName.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .reduce("", String::concat);

        StringBuilder code = new StringBuilder();
        code.append("import mg.jwe.orm.annotations.*;\n");
        code.append("import mg.jwe.orm.base.BaseModel;\n\n");
        code.append("@Table(name = \"").append(tableName).append("\")\n");
        code.append("public class ").append(className).append(" extends BaseModel {\n\n");

        // Store primary key info
        Set<String> pkColumns = new HashSet<>();
        while (primaryKeys.next()) {
            pkColumns.add(primaryKeys.getString("COLUMN_NAME"));
        }

        // Store foreign key info
        Map<String, ForeignKeyInfo> fkColumns = new HashMap<>();
        while (foreignKeys.next()) {
            String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
            String pkTableName = foreignKeys.getString("PKTABLE_NAME");
            String pkColumnName = foreignKeys.getString("PKCOLUMN_NAME");
            fkColumns.put(fkColumnName, new ForeignKeyInfo(pkTableName, pkColumnName));
        }

        // Generate fields with annotations
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            String dataType = columns.getString("TYPE_NAME");
            
            // Generate field
            if (pkColumns.contains(columnName)) {
                code.append("    @Id\n");
            }
            
            if (fkColumns.containsKey(columnName)) {
                ForeignKeyInfo fkInfo = fkColumns.get(columnName);
                String refClassName = Arrays.stream(fkInfo.pkTableName.split("_"))
                        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                        .reduce("", String::concat);
                code.append("    @ForeignKey(table = \"").append(fkInfo.pkTableName)
                    .append("\", column = \"").append(fkInfo.pkColumnName)
                    .append("\", lazy = false)\n");
                code.append("    private ").append(refClassName).append(" ")
                    .append(formater.toCamelCase(columnName.replace("id_", ""))).append(";\n\n");
            } else {
                code.append("    @Column(name = \"").append(columnName).append("\")\n");
                code.append("    private ").append(type.getJavaType(dataType)).append(" ")
                    .append(formater.toCamelCase(columnName)).append(";\n\n");
            }
        }

        // Generate getters and setters
        columns.beforeFirst();
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            String dataType = columns.getString("TYPE_NAME");
            String fieldName = formater.toCamelCase(columnName);
            String javaType = type.getJavaType(dataType);
            
            if (fkColumns.containsKey(columnName)) {
                String refClassName = Arrays.stream(fkColumns.get(columnName).pkTableName.split("_"))
                        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                        .reduce("", String::concat);
                fieldName = formater.toCamelCase(columnName.replace("id_", ""));
                javaType = refClassName;
            }

            // Getter
            code.append("    public ").append(javaType).append(" get")
                .append(fieldName.substring(0, 1).toUpperCase()).append(fieldName.substring(1))
                .append("() {\n");
            code.append("        return ").append(fieldName).append(";\n");
            code.append("    }\n\n");

            // Setter
            code.append("    public void set").append(fieldName.substring(0, 1).toUpperCase())
                .append(fieldName.substring(1)).append("(").append(javaType).append(" ")
                .append(fieldName).append(") {\n");
            code.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
            code.append("    }\n\n");
        }

        code.append("}\n");

        // Write to file
        File outputFile = new File(outputPath, className + ".java");
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(code.toString());
        }
    }
}