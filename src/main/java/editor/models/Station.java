package editor.models;
import java.util.Map;
import java.util.List;

public class Station {
    public String name;
    public int id;
    public double x;
    public double z;
    public String type; // station, semi, jct, inter, elev-we, elev-ew
    public String notes;
    public Integer y1; // For elevators
    public Integer y2; // For elevators
    
    // Complex mapping: Category -> LineName -> [Code, Branch]
    public Map<String, Map<String, String[]>> lines;
    public List<Object> areas; // Kept as object to preserve complex polygon data
    
    @Override
    public String toString() {
        return name + " (ID: " + id + ")";
    }
}