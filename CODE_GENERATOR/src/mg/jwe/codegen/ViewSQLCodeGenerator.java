package mg.jwe.codegen;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import mg.jwe.utils.Formater;
import mg.jwe.utils.TypeMapper;

public class ViewSQLCodeGenerator {
    private Formater formater;
    private TypeMapper type;

    public ViewSQLCodeGenerator() {
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

    public void generateViewFilesForSQLView(Connection connection, String viewName, String outputPath) 
        throws SQLException, IOException 
    {
        // Removes "v_"
        if (viewName.toLowerCase().startsWith("v_")) 
        { viewName = viewName.substring(2);  }

        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet columns = metaData.getColumns(null, null, "v_" + viewName, null);

        // Convert the modified view name to PascalCase for the class name
        String className = Arrays.stream(viewName.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .reduce("", String::concat);

        generateController(viewName, className, columns, outputPath);
        generateListJsp(viewName, className, columns, outputPath);
        generateViewModel(viewName, className, columns, outputPath);
    }

    private void generateViewModel(String viewName, String className, ResultSet columns, String outputPath)
        throws SQLException, IOException
    {
        StringBuilder code = new StringBuilder();

        code.append("package mg.itu.model;\n\n");

        // Create ViewData class
        code.append("public class " + formater.toPascalCase(viewName) + "  {\n");
        columns.beforeFirst();
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            String dataType = columns.getString("TYPE_NAME");
            String javaType = type.getJavaType(dataType);
            
            code.append("    private ").append(javaType).append(" ")
                .append(formater.toCamelCase(columnName)).append(";\n");
        }
        
        code.append("\n    // Getters and Setters\n");
        columns.beforeFirst();
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            String dataType = columns.getString("TYPE_NAME");
            String javaType = type.getJavaType(dataType);
            
            // Getter
            code.append("    public ").append(javaType).append(" get")
                .append(formater.toPascalCase(columnName)).append("() {\n");
            code.append("        return ").append(formater.toCamelCase(columnName)).append(";\n");
            code.append("    }\n\n");
            
            // Setter
            code.append("    public void set").append(formater.toPascalCase(columnName))
                .append("(").append(javaType).append(" ").append(formater.toCamelCase(columnName))
                .append(") {\n");
            code.append("        this.").append(formater.toCamelCase(columnName)).append(" = ")
                .append(formater.toCamelCase(columnName)).append(";\n");
            code.append("    }\n");
        }
        code.append("}\n");

        File viewDir = new File(outputPath);
        viewDir.mkdirs();

        File viewFile = new File(viewDir, formater.toPascalCase(viewName) + ".java");
        try (FileWriter writer = new FileWriter(viewFile)) {
            writer.write(code.toString());
        }
    } 

    private void generateController(String viewName, String className, ResultSet columns, String outputPath) 
        throws SQLException, IOException 
    {
        StringBuilder code = new StringBuilder();
        code.append("package mg.itu.controller;\n\n");
        
        code.append("import java.io.IOException;\n");
        code.append("import java.sql.Connection;\n");
        code.append("import java.sql.PreparedStatement;\n");
        code.append("import java.sql.ResultSet;\n");
        code.append("import java.sql.SQLException;\n");
        code.append("import java.util.ArrayList;\n");
        code.append("import java.util.List;\n");
        code.append("import javax.servlet.RequestDispatcher;\n");
        code.append("import javax.servlet.ServletException;\n");
        code.append("import javax.servlet.annotation.WebServlet;\n");
        code.append("import javax.servlet.http.HttpServlet;\n");
        code.append("import javax.servlet.http.HttpServletRequest;\n");
        code.append("import javax.servlet.http.HttpServletResponse;\n\n");
        code.append("import mg.itu.database.Database;\n\n");
        code.append("import mg.itu.model." + formater.toPascalCase(viewName) + ";\n\n");

        // Create Controller class
        code.append("@WebServlet(\"/").append(className).append("Controller\")\n");
        code.append("public class ").append(className).append("Controller extends HttpServlet {\n");
        code.append("\n");
        code.append("    private static final String VIEW_PATH = \"WEB-INF/views/")
            .append("v_" + viewName).append("/" + viewName + "-list.jsp\";\n\n");

        // doGet method
        code.append("    @Override\n");
        code.append("    protected void doGet(HttpServletRequest request, HttpServletResponse response)\n");
        code.append("            throws ServletException, IOException {\n");
        code.append("        try (Connection connection = Database.getConnection()) {\n");
        code.append("            List<"+ formater.toPascalCase(viewName) +"> viewDataList = fetchViewData(connection);\n");
        code.append("            request.setAttribute(\"viewData\", viewDataList);\n");
        code.append("            forwardToPage(request, response);\n");
        code.append("        } catch (SQLException e) {\n");
        code.append("            e.printStackTrace();\n");
        code.append("            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, \"Database error\");\n");
        code.append("        }\n");
        code.append("    }\n\n");

        // fetchViewData method
        code.append("    private List<"+ formater.toPascalCase(viewName) +"> fetchViewData(Connection connection) throws SQLException {\n");
        code.append("        List<"+ formater.toPascalCase(viewName) +"> viewDataList = new ArrayList<>();\n");
        code.append("        String sql = \"SELECT * FROM ").append("v_" + viewName).append("\";\n\n");
        code.append("        try (PreparedStatement stmt = connection.prepareStatement(sql)) {\n");
        code.append("            ResultSet rs = stmt.executeQuery();\n");
        code.append("            while (rs.next()) {\n");
        code.append("                " + formater.toPascalCase(viewName) + " data = new " + formater.toPascalCase(viewName)+ "();\n");

        // Set values from ResultSet
        columns.beforeFirst();
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            String dataType = columns.getString("TYPE_NAME");
            String getter = type.getResultSetGetter(dataType);
            
            code.append("                data.set").append(formater.toPascalCase(columnName))
                .append("(rs.").append(getter).append("(\"").append(columnName).append("\"));\n");
        }

        code.append("                viewDataList.add(data);\n");
        code.append("            }\n");
        code.append("        }\n");
        code.append("        return viewDataList;\n");
        code.append("    }\n\n");

        // forwardToPage method
        code.append("    private void forwardToPage(HttpServletRequest request, HttpServletResponse response)\n");
        code.append("            throws ServletException, IOException {\n");
        code.append("        RequestDispatcher dispatcher = request.getRequestDispatcher(VIEW_PATH);\n");
        code.append("        dispatcher.forward(request, response);\n");
        code.append("    }\n");
        code.append("}\n");

        // Write controller to file
        File controllerDir = new File(outputPath);
        controllerDir.mkdirs();
        File controllerFile = new File(controllerDir, formater.toPascalCase(viewName) + "Controller.java");
        try (FileWriter writer = new FileWriter(controllerFile)) {
            writer.write(code.toString());
        }
    }

    private void generateListJsp(String viewName, String className, ResultSet columns, String outputPath) 
        throws SQLException, IOException 
    {
        StringBuilder code = new StringBuilder();

        code.append(getBootstrapHeader());
        code.append("<%@ page import=\"java.util.List\" %>\n");
        code.append("<%@ page import=\"mg.itu.model." + formater.toPascalCase(viewName) + "\" %>\n");

        code.append("<% List<"+ formater.toPascalCase(viewName) +"> viewData = (List<"+ formater.toPascalCase(viewName) +">) request.getAttribute(\"viewData\"); %>\n\n");

        // Card container for list
        code.append("  <div class=\"card shadow-sm\">\n");
        code.append("    <div class=\"card-header bg-primary text-white\">\n");
        code.append("      <h3 class=\"mb-0\">").append(className).append(" View</h3>\n");
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
        code.append("            </tr>\n          </thead>\n\n");

        // Add table body
        code.append("          <tbody>\n");
        code.append("            <% if (viewData != null && !viewData.isEmpty()) { %>\n");
        code.append("              <% for (" + formater.toPascalCase(viewName) + " item : viewData) { %>\n");
        code.append("                <tr>\n");

        // Add table cells
        columns.beforeFirst();
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            code.append("                  <td><%= item.get").append(formater.toPascalCase(columnName))
                .append("() %></td>\n");
        }
        code.append("                </tr>\n");
        code.append("              <% } %>\n");
        code.append("            <% } else { %>\n");
        
        // Add empty state
        code.append("              <tr>\n                <td colspan=\"").append(columns.getRow())
            .append("\" class=\"text-center\">No data available.</td>\n              </tr>\n");
        code.append("            <% } %>\n");
        code.append("          </tbody>\n");
        code.append("        </table>\n");
        code.append("      </div>\n    </div>\n  </div>\n");
        code.append(getBootstrapFooter());

        // Write JSP to file
        File viewDir = new File(outputPath);
        viewDir.mkdirs();

        File viewFile = new File(viewDir, viewName + "-list.jsp");
        try (FileWriter writer = new FileWriter(viewFile)) {
            writer.write(code.toString());
        }
    }
}