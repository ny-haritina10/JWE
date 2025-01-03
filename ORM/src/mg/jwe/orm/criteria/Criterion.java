package mg.jwe.orm.criteria;

/**
 * Represents a search criterion used for building dynamic database queries.
 * <p>
 * This class encapsulates a single condition in a database query, consisting of:
 * <ul>
 *   <li>A column name</li>
 *   <li>An operator (e.g., "=", ">", "<", "LIKE")</li>
 *   <li>A value to compare against</li>
 * </ul>
 * </p>
 * <p>
 * Supported operators include:
 * <ul>
 *   <li>"=" - Exact match</li>
 *   <li>">" - Greater than</li>
 *   <li>">=" - Greater than or equal</li>
 *   <li>"<" - Less than</li>
 *   <li>"<=" - Less than or equal</li>
 *   <li>"!=" - Not equal</li>
 *   <li>"LIKE" - Pattern matching</li>
 *   <li>"IN" - Multiple values</li>
 * </ul>
 * </p>
 * 
 * @see mg.jwe.orm.base.BaseModel#findByCriteria
 * @see mg.jwe.orm.base.BaseModel#findByAnyCriteria
 */
public class Criterion {

    private String column;
    private String operator;
    private Object value;
    
    /**
     * Constructs a new search criterion.
     *
     * @param column The database column name to query against
     * @param operator The comparison operator to use
     * @param value The value to compare with
     */
    public Criterion(String column, String operator, Object value) {
        this.column = column;
        this.operator = operator;
        this.value = value;
    }
    
    /**
     * Gets the column name for this criterion.
     *
     * @return The database column name
     */
    public String getColumn() 
    { return column; }

    /**
     * Gets the operator for this criterion.
     *
     * @return The comparison operator
     */
    public String getOperator() 
    { return operator; }

    /**
     * Gets the comparison value for this criterion.
     *
     * @return The value to compare against
     */
    public Object getValue() 
    { return value; }
}