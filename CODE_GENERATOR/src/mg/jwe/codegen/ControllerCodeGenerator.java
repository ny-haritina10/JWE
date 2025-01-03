package mg.jwe.codegen;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import mg.jwe.utils.Formater;
import mg.jwe.utils.TypeMapper;

import java.util.Arrays;

public class ControllerCodeGenerator {

    private Formater formater;
    private TypeMapper type;

    public ControllerCodeGenerator() {
        this.formater = new Formater();
        this.type = new TypeMapper();
    }
    
    public void generateControllerForTable(Connection connection, String tableName, String outputPath) 
        throws SQLException, IOException 
    {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet columns = metaData.getColumns(null, null, tableName, null);
        ResultSet foreignKeys = metaData.getImportedKeys(null, null, tableName);
        
        // Generate class name (convert to PascalCase)
        String className = Arrays.stream(tableName.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .reduce("", String::concat);
        
        // Determine if there are foreign keys
        boolean hasForeignKeys = false;
        List<String> fkColumnNames = new ArrayList<>();
        while (foreignKeys.next()) {
            hasForeignKeys = true;
            fkColumnNames.add(foreignKeys.getString("FKCOLUMN_NAME"));
        }
    
        // Reset foreign keys result set
        foreignKeys = metaData.getImportedKeys(null, null, tableName);
    
        StringBuilder code = new StringBuilder();
        code.append("package mg.itu.controller;\n\n");
        
        // Import statements
        code.append("import java.io.IOException;\n");
        code.append("import java.sql.Connection;\n");
        code.append("import java.sql.SQLException;\n");
        code.append("import javax.servlet.RequestDispatcher;\n");
        code.append("import javax.servlet.ServletException;\n");
        code.append("import javax.servlet.annotation.WebServlet;\n");
        code.append("import javax.servlet.http.HttpServlet;\n");
        code.append("import javax.servlet.http.HttpServletRequest;\n");
        code.append("import javax.servlet.http.HttpServletResponse;\n\n");
        
        // Import database and model classes
        code.append("import mg.itu.database.Database;\n");
        code.append("import mg.itu.model.").append(className).append(";\n");
        
        // If there are foreign keys, import those model classes
        if (hasForeignKeys) {
            while (foreignKeys.next()) {
                String pkTableName = foreignKeys.getString("PKTABLE_NAME");
                String refClassName = Arrays.stream(pkTableName.split("_"))
                        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                        .reduce("", String::concat);
                code.append("import mg.itu.model.").append(refClassName).append(";\n");
            }
        }
        
        // WebServelt Annonation
        code.append("\n@WebServlet(\"/").append(className).append("Controller\")\n");
        code.append("public class ").append(className).append("Controller extends HttpServlet {\n");
        code.append("    private static final String VIEW_BASE_PATH = \"WEB-INF/views/")
            .append(tableName.toLowerCase()).append("/\";\n\n");
    
        // doGet method
        code.append("    @Override\n");
        code.append("    protected void doGet(HttpServletRequest request, HttpServletResponse response)\n");
        code.append("        throws ServletException, IOException \n");
        code.append("    {\n");
        code.append("        try (Connection connection = Database.getConnection()) {\n");
        code.append("            String mode = request.getParameter(\"mode\");\n\n");
        code.append("            switch (mode) {\n");
        code.append("                case \"form\":\n");
        
        // If has foreign keys, prepare form with foreign key objects
        if (hasForeignKeys) {
            code.append("                    prepareForm(connection, request, response);\n");
        } else {
            code.append("                    forwardToPage(request, response, \"form.jsp\");\n");
        }
        
        code.append("                    break;\n\n");
        code.append("                case \"list\":\n");
        code.append("                    render").append(className).append("List(connection, request, response);\n");
        code.append("                    break;\n\n");
        code.append("                case \"update\":\n");
        code.append("                    handleUpdate(connection, request, response);\n");
        code.append("                    break;\n\n");
        code.append("                case \"delete\":\n");
        code.append("                    handleDelete(connection, request, response);\n");
        code.append("                    break;\n\n");
        code.append("                default:\n");
        code.append("                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, \"Invalid mode\");\n");
        code.append("            }\n");
        code.append("        } \n");
        code.append("        catch (Exception e) \n");
        code.append("        { e.printStackTrace(); }\n");
        code.append("    }\n\n");
    
        // doPost method
        code.append("    @Override\n");
        code.append("    protected void doPost(HttpServletRequest request, HttpServletResponse response)\n");
        code.append("        throws ServletException, IOException \n");
        code.append("    {\n");
        code.append("        try (Connection connection = Database.getConnection()) {\n");
        code.append("            String mode = request.getParameter(\"mode\");\n\n");
        code.append("            if (\"insert\".equals(mode)) {\n");
        code.append("                ").append(className).append(" ").append(formater.toCamelCase(className)).append(" = mapRequestTo").append(className).append("(request");
        
        if (hasForeignKeys) {
            code.append(", connection");
        }
        
        code.append(");\n");
        code.append("                ").append(formater.toCamelCase(className)).append(".save(connection);\n");
        code.append("            } \n");
        code.append("            else if (\"update\".equals(mode)) {\n");
        code.append("                ").append(className).append(" ").append(formater.toCamelCase(className)).append(" = mapRequestTo").append(className).append("(request");
        
        if (hasForeignKeys) {
            code.append(", connection");
        }
        
        code.append(");\n");
        code.append("                ").append(formater.toCamelCase(className)).append(".update(connection);\n");
        code.append("            }\n\n");
        code.append("            response.sendRedirect(\"").append(className).append("Controller?mode=list\");\n");
        code.append("        } \n");
        code.append("        catch (Exception e) \n");
        code.append("        { e.printStackTrace(); }\n");
        code.append("    }\n\n");
    
        // render list method
        code.append("    private void render").append(className).append("List(Connection connection, HttpServletRequest request, HttpServletResponse response)\n");
        code.append("        throws ServletException, IOException, SQLException \n");
        code.append("    {\n");
        code.append("        ").append(className).append("[] ").append(formater.toCamelCase(className)).append(" = ").append(className).append(".getAll(connection, ").append(className).append(".class);\n");
        code.append("        request.setAttribute(\"").append(formater.toCamelCase(className)).append("\", ").append(formater.toCamelCase(className)).append(");\n\n");
        code.append("        forwardToPage(request, response, \"list.jsp\");\n");
        code.append("    }\n\n");
    
        // prepare form method (only if foreign keys exist)
        if (hasForeignKeys) {
            code.append("    private void prepareForm(Connection connection, HttpServletRequest request, HttpServletResponse response)\n");
            code.append("        throws ServletException, IOException, SQLException \n");
            code.append("    {\n");
            
            // Prepare foreign key objects
            foreignKeys = metaData.getImportedKeys(null, null, tableName);
            while (foreignKeys.next()) {
                String pkTableName = foreignKeys.getString("PKTABLE_NAME");
                String refClassName = Arrays.stream(pkTableName.split("_"))
                        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                        .reduce("", String::concat);
                code.append("        ").append(refClassName).append("[] ")
                    .append(formater.toCamelCase(refClassName)).append(" = ").append(refClassName)
                    .append(".getAll(connection, ").append(refClassName).append(".class);\n");
                // TODO: typo fix 
                code.append("        request.setAttribute(\"")
                    .append(formater.toCamelCase(refClassName)).append("\", ")
                    .append(formater.toCamelCase(refClassName)).append(");\n");
            }
            
            // Reset foreign keys result set
            foreignKeys = metaData.getImportedKeys(null, null, tableName);
            
            code.append("        String idParam = request.getParameter(\"id\");\n");
            code.append("        if (idParam != null && !idParam.equals(\"\")) {\n");
            code.append("            Integer id = Integer.valueOf(idParam);\n");
            code.append("            ").append(className).append(" ").append(formater.toCamelCase(className))
                .append(" = ").append(className).append(".findById(connection, ").append(className)
                .append(".class, id);\n\n");
            code.append("            request.setAttribute(\"").append(formater.toCamelCase(className))
                .append("\", ").append(formater.toCamelCase(className)).append(");\n");
            code.append("        }\n\n");
            code.append("        forwardToPage(request, response, \"form.jsp\");\n");
            code.append("    }\n\n");
        }
    
        // forward to page method
        code.append("    private void forwardToPage(HttpServletRequest request, HttpServletResponse response, String page)\n");
        code.append("        throws ServletException, IOException \n");
        code.append("    {\n");
        code.append("        RequestDispatcher dispatcher = request.getRequestDispatcher(VIEW_BASE_PATH + page);\n");
        code.append("        dispatcher.forward(request, response);\n");
        code.append("    }\n\n");
    
        // map request to object method
        code.append("    private ").append(className).append(" mapRequestTo").append(className).append("(HttpServletRequest request");
        
        if (hasForeignKeys) {
            code.append(", Connection connection");
        }
        
        code.append(") ");
        
        if (hasForeignKeys) {
            code.append("throws SQLException ");
        }
        
        code.append("{\n");
        code.append("        ").append(className).append(" ").append(formater.toCamelCase(className)).append(" = new ").append(className).append("();\n\n");
        
        // Reset columns result set
        columns.beforeFirst();
        
        // Add setters for non-FK columns and handle FK columns
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            String dataType = columns.getString("TYPE_NAME");
            
            // Skip primary key column
            if (columnName.equals("id") || columnName.endsWith("_id") || columnName.startsWith("id_")) continue;
            
            // Handle special types
            String javaType = type.getJavaType(dataType);
            String setter = "set" + formater.toCamelCase(columnName).substring(0, 1).toUpperCase() + 
                            formater.toCamelCase(columnName).substring(1);
            
            if (javaType.equals("java.math.BigDecimal")) {
                code.append("        if (request.getParameter(\"").append(formater.toCamelCase(columnName))
                    .append("\") != null && !request.getParameter(\"")
                    .append(formater.toCamelCase(columnName)).append("\").equals(\"\")) {\n");
                code.append("            ").append(formater.toCamelCase(className)).append(".")
                    .append(setter).append("(new BigDecimal(request.getParameter(\"")
                    .append(formater.toCamelCase(columnName)).append("\")));\n");
                code.append("        }\n\n");
            } else {
                code.append("        ").append(formater.toCamelCase(className)).append(".")
                    .append(setter).append("(request.getParameter(\"")
                    .append(formater.toCamelCase(columnName)).append("\"));\n");
            }
        }
        
        // Handle foreign key columns
        foreignKeys = metaData.getImportedKeys(null, null, tableName);
        while (foreignKeys.next()) {
            String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
            String pkTableName = foreignKeys.getString("PKTABLE_NAME");
            
            String refClassName = Arrays.stream(pkTableName.split("_"))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .reduce("", String::concat);
            
            code.append("        String ").append(formater.toCamelCase(refClassName))
                .append("Id = request.getParameter(\"").append(formater.toCamelCase(fkColumnName)).append("\");\n");
            
            code.append("        if (").append(formater.toCamelCase(refClassName)).append("Id != null && !")
                .append(formater.toCamelCase(refClassName)).append("Id.equals(\"\")) {\n");
            
            code.append("            ").append(refClassName).append(" ")
                .append(formater.toCamelCase(refClassName)).append(" = ")
                .append(refClassName).append(".findById(connection, ")
                .append(refClassName).append(".class, Integer.valueOf(")
                .append(formater.toCamelCase(refClassName)).append("Id));\n");
            
            // Assuming the setter method follows the pattern setCategory(Categories category)
            code.append("            ").append(formater.toCamelCase(className)).append(".set")
                .append(refClassName).append("(").append(formater.toCamelCase(refClassName)).append(");\n");
            
            code.append("        }\n\n");
        }
        
        // Handle ID column
        code.append("        String idParam = request.getParameter(\"id\");\n");
        code.append("        if (idParam != null && !idParam.equals(\"\")) {\n");
        code.append("            ").append(formater.toCamelCase(className)).append(".setId(Integer.valueOf(idParam));\n");
        code.append("        }\n\n");
        
        code.append("        return ").append(formater.toCamelCase(className)).append(";\n");
        code.append("    }\n\n");
    
        // handle update method
        code.append("    private void handleUpdate(Connection connection, HttpServletRequest request, HttpServletResponse response)\n");
        code.append("        throws ServletException, IOException, SQLException\n");
        code.append("    {\n");
        
        if (hasForeignKeys) {
            code.append("        prepareForm(connection, request, response);\n");
        } else {
            code.append("        String idParam = request.getParameter(\"id\");\n");
            code.append("        if (idParam != null && !idParam.equals(\"\")) {\n");
            code.append("            Integer id = Integer.valueOf(idParam);\n");
            code.append("            ").append(className).append(" ").append(formater.toCamelCase(className))
                .append(" = ").append(className).append(".findById(connection, ").append(className)
                .append(".class, id);\n\n");
            code.append("            request.setAttribute(\"").append(formater.toCamelCase(className))
                .append("\", ").append(formater.toCamelCase(className)).append(");\n");
            code.append("            forwardToPage(request, response, \"form.jsp\");\n");
            code.append("        } \n\n");
            code.append("        else {\n");
            code.append("            response.sendError(HttpServletResponse.SC_BAD_REQUEST, \"Missing ID for update\");\n");
            code.append("        }\n");
        }
        code.append("    }\n\n");

        code.append("    private void handleDelete(Connection connection, HttpServletRequest request, HttpServletResponse response)\n");
        code.append("        throws ServletException, IOException, SQLException \n");
        code.append("    {\n");
        code.append("        String idParam = request.getParameter(\"id\");\n\n");
        code.append("        if (idParam != null && !idParam.equals(\"\")) {\n");
        code.append("            Integer id = Integer.valueOf(idParam);\n");
        code.append("            ").append(className).append(" ").append(formater.toCamelCase(className))
            .append(" = ").append(className).append(".findById(connection, ").append(className)
            .append(".class, id);\n\n");
        code.append("            ").append(formater.toCamelCase(className)).append(".delete(connection);\n\n");
        
        code.append("            render").append(className).append("List(connection, request, response);\n");
        code.append("        } \n\n");
        code.append("        else {\n");
        code.append("            response.sendError(HttpServletResponse.SC_BAD_REQUEST, \"Missing ID for delete\");\n");
        code.append("        }\n");
        code.append("    }\n\n");
        code.append("}\n");


        // Write to file
        File outputFile = new File(outputPath, className + "Controller.java");
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(code.toString());
        }
    }
}