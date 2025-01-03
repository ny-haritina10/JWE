package mg.jwe.main;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import mg.jwe.generator.Generator;

public class Main {
    public static void main(String[] args) {
        try 
        { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } 
        
        catch (Exception e) 
        { e.printStackTrace(); }

        SwingUtilities.invokeLater(() -> {
            new Generator().setVisible(true);
        });
    }
}