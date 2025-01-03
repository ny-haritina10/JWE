package mg.jwe.generator;

import javax.swing.*;

import mg.jwe.codegen.ControllerCodeGenerator;
import mg.jwe.codegen.ModelCodeGenerator;
import mg.jwe.codegen.ViewHTMLCodeGenerator;
import mg.jwe.codegen.ViewSQLCodeGenerator;

import java.awt.*;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.List;

public class Generator extends JFrame {

    private JComboBox<String> dbTypeCombo;
    private JTextField hostField, portField, dbNameField, userField;
    private JPasswordField passwordField;
    private JCheckBox[] tableCheckboxes;
    private JTextField outputPathField;
    private JPanel tablesPanel;
    private Connection connection;
    private JCheckBox generateCrudCheckbox;
    private JCheckBox generateSqlViewCheckbox;
    private JButton generateButton;  

    private ControllerCodeGenerator controller;
    private ModelCodeGenerator model;
    private ViewHTMLCodeGenerator view;
    private ViewSQLCodeGenerator sqlView;

    public Generator() {
        // UI ============================================================ 
        setTitle("CRUD Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Main panel with padding
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Database selection panel
        JPanel dbPanel = new JPanel(new GridLayout(7, 2, 5, 5));
        dbPanel.setBorder(BorderFactory.createTitledBorder("Database Connection"));
        
        String[] dbTypes = { "PostgreSQL" };

        dbTypeCombo = new JComboBox<>(dbTypes);
        hostField = new JTextField("localhost");
        portField = new JTextField("5432");
        dbNameField = new JTextField();
        userField = new JTextField();
        passwordField = new JPasswordField();
        generateButton = new JButton("Generate");  
        
        dbPanel.add(new JLabel("Database Type:"));
        dbPanel.add(dbTypeCombo);
        dbPanel.add(new JLabel("Host:"));
        dbPanel.add(hostField);
        dbPanel.add(new JLabel("Port:"));
        dbPanel.add(portField);
        dbPanel.add(new JLabel("Database Name:"));
        dbPanel.add(dbNameField);
        dbPanel.add(new JLabel("Username:"));
        dbPanel.add(userField);
        dbPanel.add(new JLabel("Password:"));
        dbPanel.add(passwordField);
        
        JButton connectButton = new JButton("Connect");
        dbPanel.add(connectButton);

        // Tables panel
        tablesPanel = new JPanel();
        tablesPanel.setLayout(new BoxLayout(tablesPanel, BoxLayout.Y_AXIS));
        tablesPanel.setBorder(BorderFactory.createTitledBorder("Select Tables"));
        
        // Output path panel
        JPanel pathPanel = new JPanel(new BorderLayout(5, 5));
        pathPanel.setBorder(BorderFactory.createTitledBorder("Output Location"));
        outputPathField = new JTextField();
        JButton browseButton = new JButton("Browse");
        pathPanel.add(outputPathField, BorderLayout.CENTER);
        pathPanel.add(browseButton, BorderLayout.EAST);

        // Generation options panel
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Generation Options"));
        
        generateCrudCheckbox = new JCheckBox("Generate CRUD", false);
        generateSqlViewCheckbox = new JCheckBox("Generate SQL View Code", false);
        
        optionsPanel.add(generateCrudCheckbox);
        optionsPanel.add(generateSqlViewCheckbox);

        // Add components to main panel
        mainPanel.add(dbPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(new JScrollPane(tablesPanel));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(pathPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(optionsPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(generateButton);

        add(new JScrollPane(mainPanel));

        // Connect button action
        connectButton.addActionListener(e -> connectToDatabase());

        // Browse button action
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                outputPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        // Generate button action
        generateButton.addActionListener(e -> generateCrud());

        pack();
        setLocationRelativeTo(null);

        // INIT ============================================================ 
        this.controller = new ControllerCodeGenerator();
        this.model = new ModelCodeGenerator();
        this.view = new ViewHTMLCodeGenerator();
        this.sqlView = new ViewSQLCodeGenerator();
    }

    private void generateCrud() {
        if (connection == null) {
            JOptionPane.showMessageDialog(this, "Please connect to database first!");
            return;
        }

        String baseOutputPath = outputPathField.getText();
        if (baseOutputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select output directory!");
            return;
        }

        try {
            for (JCheckBox checkbox : tableCheckboxes) {
                if (checkbox.isSelected()) {
                    String displayName = checkbox.getText();
                    // Extract table name and type from display name (e.g., "table_name (TABLE)")
                    String[] parts = displayName.split(" \\(");
                    String name = parts[0];
                    String type = parts[1].replace(")", "");
                    
                    // Create directory for this table/view
                    String outputPath = baseOutputPath + File.separator + name;
                    new File(outputPath).mkdirs();
                    
                    if (type.equals("VIEW") && generateSqlViewCheckbox.isSelected()) {
                        // Generate SQL view code
                        sqlView.generateViewFilesForSQLView(connection, name, outputPath);
                    } else if (type.equals("TABLE") && generateCrudCheckbox.isSelected()) {
                        // Generate CRUD components
                        model.generateModelForTable(connection, name, outputPath);
                        controller.generateControllerForTable(connection, name, outputPath);
                        view.generateViewsForTable(connection, name, outputPath);
                    }
                }
            }
            JOptionPane.showMessageDialog(this, "Generation completed successfully!");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error generating files: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void loadTables() throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet tablesAndViews = metaData.getTables(null, null, "%", new String[]{"TABLE", "VIEW"});
    
        tablesPanel.removeAll();
        List<JCheckBox> checkboxList = new ArrayList<>();
    
        while (tablesAndViews.next()) {
            String name = tablesAndViews.getString("TABLE_NAME");
            String type = tablesAndViews.getString("TABLE_TYPE"); 
            
            // Format the display name to distinguish between tables and views
            String displayName = name + " (" + type + ")";
            JCheckBox checkbox = new JCheckBox(displayName);
            checkboxList.add(checkbox);
            tablesPanel.add(checkbox);
        }
    
        tableCheckboxes = checkboxList.toArray(new JCheckBox[0]);
        tablesPanel.revalidate();
        tablesPanel.repaint();
    }

    private void connectToDatabase() {
        try {
            String url = getConnectionUrl();
            Properties props = new Properties();
            props.setProperty("user", userField.getText());
            props.setProperty("password", new String(passwordField.getPassword()));
            
            connection = DriverManager.getConnection(url, props);
            loadTables();
            JOptionPane.showMessageDialog(this, "Connected successfully!");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private String getConnectionUrl() {
        String dbType = (String) dbTypeCombo.getSelectedItem();
        String host = hostField.getText();
        String port = portField.getText();
        String dbName = dbNameField.getText();
        
        switch (dbType) {
            case "PostgreSQL":
                return "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
            case "MySQL":
                return "jdbc:mysql://" + host + ":" + port + "/" + dbName;
            case "Oracle":
                return "jdbc:oracle:thin:@" + host + ":" + port + ":" + dbName;
            default:
                return "";
        }
    }
}