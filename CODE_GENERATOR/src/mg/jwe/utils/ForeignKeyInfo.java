package mg.jwe.utils;

public class ForeignKeyInfo {
    
    // TODO: fix later
    public String pkTableName;
    public String pkColumnName;

    public ForeignKeyInfo(String pkTableName, String pkColumnName) {
        this.pkTableName = pkTableName;
        this.pkColumnName = pkColumnName;
    }
}
