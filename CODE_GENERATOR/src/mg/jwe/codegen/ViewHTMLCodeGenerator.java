package mg.jwe.codegen;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

import mg.jwe.utils.ForeignKeyInfo;
import mg.jwe.utils.Formater;
import mg.jwe.utils.TypeMapper;

public class ViewHTMLCodeGenerator {
    private Formater formater;
    private TypeMapper type;

    public ViewHTMLCodeGenerator() {
        this.formater = new Formater();
        this.type = new TypeMapper();
    }

    private String getBootstrapHeader() {
        return "<!DOCTYPE html>\n" +
               "<html lang=\"en\">\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n" +
               "    <script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js\"></script>\n" +
               "</head>\n" +
               "<body class=\"bg-light\">\n" +
               "<div class=\"container py-5\">\n";
    }

    private String getBootstrapFooter() {
        return "</div>\n</body>\n</html>";
    }

    public void generateViewsForTable(Connection connection, String tableName, String outputPath) 
        throws SQLException, IOException 
    {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet columns = metaData.getColumns(null, null, tableName, null);
        ResultSet foreignKeys = metaData.getImportedKeys(null, null, tableName);

        String className = Arrays.stream(tableName.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .reduce("", String::concat);

        Map<String, ForeignKeyInfo> fkColumns = new HashMap<>();
        while (foreignKeys.next()) {
            String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
            String pkTableName = foreignKeys.getString("PKTABLE_NAME");
            String pkColumnName = foreignKeys.getString("PKCOLUMN_NAME");
            fkColumns.put(fkColumnName, new ForeignKeyInfo(pkTableName, pkColumnName));
        }

        generateFormJsp(className, columns, fkColumns, outputPath);
        generateListJsp(className, columns, fkColumns, outputPath);
    }

    private void generateFormJsp(String className, ResultSet columns, Map<String, ForeignKeyInfo> fkColumns, String outputPath) 
        throws SQLException, IOException 
    {
        StringBuilder code = new StringBuilder();
        code.append(getBootstrapHeader());
        code.append("<%@ page import=\"mg.itu.model.").append(className).append("\" %>\n");

        // Add imports for foreign key classes
        for (ForeignKeyInfo fkInfo : fkColumns.values()) {
            String refClassName = Arrays.stream(fkInfo.pkTableName.split("_"))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .reduce("", String::concat);
            code.append("<%@ page import=\"mg.itu.model.").append(refClassName).append("\" %>\n");
        }

        // Add instance variables
        String instanceName = formater.toCamelCase(className);
        code.append("<%\n  ").append(className).append(" ").append(instanceName)
            .append(" = (").append(className).append(") request.getAttribute(\"")
            .append(instanceName).append("\");\n");

        // Add foreign key arrays
        for (ForeignKeyInfo fkInfo : fkColumns.values()) {
            String refClassName = Arrays.stream(fkInfo.pkTableName.split("_"))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .reduce("", String::concat);
            String arrayName = formater.toCamelCase(fkInfo.pkTableName);
            code.append("  ").append(refClassName).append("[] ").append(arrayName)
                .append(" = (").append(refClassName).append("[]) request.getAttribute(\"")
                .append(arrayName).append("\");\n");
        }
        code.append("%>\n\n");

        // Card container for form
        code.append("  <div class=\"card shadow-sm\">\n");
        code.append("    <div class=\"card-header bg-primary text-white\">\n");
        code.append("      <h3 class=\"mb-0\">").append(className).append(" Form</h3>\n");
        code.append("    </div>\n");
        code.append("    <div class=\"card-body\">\n");
        code.append("      <form action=\"").append(className).append("Controller\" method=\"post\">\n");

        // Add form fields
        columns.beforeFirst();
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            String dataType = columns.getString("TYPE_NAME");
            
            if (!columnName.equalsIgnoreCase("id")) {
                code.append("        <div class=\"mb-3\">\n");
                code.append("          <label class=\"form-label\">")
                    .append(formater.toPascalCase(columnName)).append("</label>\n");

                if (fkColumns.containsKey(columnName)) {
                    // Generate select for foreign keys
                    ForeignKeyInfo fkInfo = fkColumns.get(columnName);
                    String arrayName = formater.toCamelCase(fkInfo.pkTableName);
                    String fieldName = formater.toCamelCase(columnName.replace("id_", ""));
                    

                    // TODO: typo fix
                    code.append("          <select class=\"form-select\" name=\"").append(formater.toCamelCase(columnName)).append("\">\n");
                    code.append("            <option value=\"\">Select a ").append(fieldName).append("</option>\n");
                    code.append("            <% if (").append(arrayName).append(" != null) {\n");
                    code.append("                 for (").append(formater.toPascalCase(fkInfo.pkTableName));
                    code.append(" item : ").append(arrayName).append(") { %>\n");
                    code.append("              <option value=\"<%= item.getId() %>\" \n");
                    code.append("                <%= ").append(instanceName).append(" != null && ")
                        .append(instanceName).append(".get").append(formater.toPascalCase(fieldName))
                        .append("() != null && ").append(instanceName).append(".get")
                        .append(formater.toPascalCase(fieldName))
                        .append("().getId().equals(item.getId()) ? \"selected\" : \"\" %>>\n");
                    code.append("                <%= item.getLabel() %>\n");
                    code.append("              </option>\n");
                    code.append("            <% }\n              } %>\n");
                    code.append("          </select>\n");
                } else {
                    // Generate regular input fields
                    String inputType = type.getInputType(dataType);
                    code.append("          <input type=\"").append(inputType).append("\" ");
                    if (inputType.equals("number")) {
                        code.append("step=\"0.01\" ");
                    }
                    code.append("class=\"form-control\" name=\"").append(columnName).append("\" ");
                    code.append("value=\"<%= ").append(instanceName).append(" != null ? ")
                        .append(instanceName).append(".get").append(formater.toPascalCase(columnName))
                        .append("() : \"\" %>\">\n");
                }
                code.append("        </div>\n");
            }
        }

        // Add submit button and hidden fields
        code.append("        <div class=\"mt-4\">\n");
        code.append("          <button type=\"submit\" class=\"btn btn-primary\">Submit</button>\n");
        code.append("          <a href=\"").append(className).append("Controller\" class=\"btn btn-secondary ms-2\">Cancel</a>\n");
        code.append("        </div>\n\n");
        code.append("        <input type=\"hidden\" name=\"id\" value=\"<%= ")
            .append(instanceName).append(" != null ? ").append(instanceName)
            .append(".getId() : \"\" %>\">\n");
        code.append("        <input type=\"hidden\" name=\"mode\" value=\"<%= ")
            .append(instanceName).append(" == null ? \"insert\" : \"update\" %>\">\n");
        code.append("      </form>\n");
        code.append("    </div>\n");
        code.append("  </div>\n");
        code.append(getBootstrapFooter());

        // Write to file
        File outputFile = new File(outputPath, "form.jsp");
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(code.toString());
        }
    }

    private void generateListJsp(String className, ResultSet columns, Map<String, ForeignKeyInfo> fkColumns, String outputPath) 
        throws SQLException, IOException 
    {
        StringBuilder code = new StringBuilder();
        code.append(getBootstrapHeader());
        code.append("<%@ page import=\"mg.itu.model.").append(className).append("\" %>\n");
        code.append("<%\n  ").append(className).append("[] ")
            .append(formater.toCamelCase(className)).append(" = (")
            .append(className).append("[]) request.getAttribute(\"")
            .append(formater.toCamelCase(className)).append("\");\n%>\n\n");

        // Card container for list
        code.append("  <div class=\"card shadow-sm\">\n");
        code.append("    <div class=\"card-header bg-primary text-white d-flex justify-content-between align-items-center\">\n");
        code.append("      <h3 class=\"mb-0\">").append(className).append(" List</h3>\n");
        code.append("      <a href=\"").append(className).append("Controller?mode=form\" class=\"btn btn-light\">Add New</a>\n");
        code.append("    </div>\n");
        code.append("    <div class=\"card-body\">\n");
        code.append("      <div class=\"table-responsive\">\n");
        code.append("        <table class=\"table table-striped table-hover\">\n");
        code.append("          <thead class=\"table-light\">\n            <tr>\n");

        // Add table headers
        columns.beforeFirst();
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            code.append("              <th>").append(formater.toPascalCase(columnName)).append("</th>\n");
        }
        code.append("              <th>Actions</th>\n            </tr>\n          </thead>\n\n");

        // Add table body
        code.append("          <tbody>\n            <%\n              if (")
            .append(formater.toCamelCase(className)).append(" != null) {\n")
            .append("                for (").append(className).append(" item : ")
            .append(formater.toCamelCase(className)).append(") {\n            %>\n");
        code.append("            <tr>\n");

        // Add table cells
        columns.beforeFirst();
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            code.append("              <td>");
            if (fkColumns.containsKey(columnName)) {
                String fieldName = formater.toCamelCase(columnName.replace("id_", ""));
                code.append("<%= item.get").append(formater.toPascalCase(fieldName))
                    .append("().getLabel() %>");
            } else {
                code.append("<%= item.get").append(formater.toPascalCase(columnName)).append("() %>");
            }
            code.append("</td>\n");
        }

        // Add action buttons
        code.append("              <td>\n");
        code.append("                <div class=\"btn-group btn-group-sm\">\n");
        code.append("                  <a href=\"").append(className)
            .append("Controller?mode=update&id=<%= item.getId() %>\" ")
            .append("class=\"btn btn-primary\">Edit</a>\n");
        code.append("                  <a href=\"").append(className)
            .append("Controller?mode=delete&id=<%= item.getId() %>\" ")
            .append("class=\"btn btn-danger\" onclick=\"return confirm('Are you sure?')\">Delete</a>\n");
        code.append("                </div>\n");
        code.append("              </td>\n            </tr>\n");
        code.append("            <%\n                }\n              } else {\n            %>\n");

        // Add empty state
        code.append("            <tr>\n              <td colspan=\"").append(columns.getRow() + 1)
            .append("\" class=\"text-center\">No ").append(formater.toCamelCase(className))
            .append("s available.</td>\n            </tr>\n");
        code.append("            <%\n              }\n            %>\n          </tbody>\n");
        code.append("        </table>\n      </div>\n    </div>\n  </div>\n");
        code.append(getBootstrapFooter());

        // Write to file
        File outputFile = new File(outputPath, "list.jsp");
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(code.toString());
        }
    }
}