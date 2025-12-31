package editor.models;
import java.util.Map;
import java.util.List;

public class LineData {
    public String prefix;
    public String code;
    public String color;
    public int y;
    public Map<String, Branch> branches;
    
    public static class Branch {
        public List<Double[]> vertices;
        public List<Object> stations;
    }
}