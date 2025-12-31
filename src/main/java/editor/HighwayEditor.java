package editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import editor.models.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class HighwayEditor extends JFrame {
    private HighwaysData data;
    
    // Navigation
    private DefaultListModel<Station> stationListModel = new DefaultListModel<>();
    private JList<Station> stationList = new JList<>(stationListModel);
    private DefaultListModel<String> lineListModel = new DefaultListModel<>();
    private JList<String> lineList = new JList<>(lineListModel);
    private JTabbedPane leftTabs = new JTabbedPane();
    private JTextField searchField = new JTextField();
    
    // Map
    private MapPanel mapPanel;
    
    // Editor Logic
    private CardLayout rightCardLayout = new CardLayout();
    private JPanel rightEditorContainer = new JPanel(rightCardLayout);
    
    // Station Fields
    private JTextField stNameField = new JTextField(), stXField = new JTextField(), stZField = new JTextField();
    private JComboBox<String> stTypeBox = new JComboBox<>(new String[]{"station", "semi", "jct", "inter", "elev-we", "elev-ew"});
    private JTextArea stNotesArea = new JTextArea(3, 20);
    private JTextField stY1Field = new JTextField(), stY2Field = new JTextField();
    private JLabel stY1Label = new JLabel("Y1:"), stY2Label = new JLabel("Y2:");
    private JPanel elevatorPanel;
    private DefaultTableModel connectionModel = new DefaultTableModel(new Object[]{"Category", "Line", "Map Number", "Branch"}, 0);
    private JTable connectionTable = new JTable(connectionModel) {
        public TableCellEditor getCellEditor(int row, int column) {
            int modelColumn = convertColumnIndexToModel(column);
            if (data == null) return super.getCellEditor(row, column);
            if (modelColumn == 0) {
                JComboBox<String> cb = new JComboBox<>(new Vector<>(data.lines.keySet())); cb.setEditable(true);
                return new DefaultCellEditor(cb);
            }
            if (modelColumn == 1) {
                String cat = (String) getValueAt(row, 0);
                Vector<String> lines = new Vector<>();
                if (cat != null && data.lines.containsKey(cat)) lines.addAll(data.lines.get(cat).keySet());
                else data.lines.values().forEach(m -> lines.addAll(m.keySet()));
                JComboBox<String> cb = new JComboBox<>(lines); cb.setEditable(true);
                return new DefaultCellEditor(cb);
            }
            if (modelColumn == 3) {
                Set<String> branches = new HashSet<>(); branches.add("Main line");
                String cat = (String) getValueAt(row, 0); String ln = (String) getValueAt(row, 1);
                if (cat != null && ln != null && data.lines.containsKey(cat) && data.lines.get(cat).containsKey(ln)) branches.addAll(data.lines.get(cat).get(ln).branches.keySet());
                JComboBox<String> cb = new JComboBox<>(new Vector<>(branches)); cb.setEditable(true);
                return new DefaultCellEditor(cb);
            }
            return super.getCellEditor(row, column);
        }
    };
    
    // Line Fields
    private JTextField lnCodeField = new JTextField(), lnPrefixField = new JTextField(), lnYField = new JTextField(), lnColorField = new JTextField();
    private JPanel colorPreview = new JPanel();
    private JToggleButton editPathBtn = new JToggleButton("Edit Path");
    private JCheckBox orthoBox = new JCheckBox("Orthogonal Snap");
    
    // Vertex Editing Fields
    private JTextField vertXField = new JTextField(), vertZField = new JTextField();
    private JButton vertUpdateBtn = new JButton("Move");
    
    public HighwayEditor() {
        setTitle("WorldMC Ice Highway Editor");
        setSize(1400, 900);
        setMinimumSize(new Dimension(1000, 750));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // --- LEFT PANEL ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(new TitledBorder("Navigation"));
        searchField.addCaretListener(e -> filterLists(searchField.getText()));
        leftPanel.add(searchField, BorderLayout.NORTH);
        leftTabs.addTab("Stations", new JScrollPane(stationList));
        leftTabs.addTab("Lines", new JScrollPane(lineList));
        leftPanel.add(leftTabs, BorderLayout.CENTER);
        
        JPanel leftButtonPanel = new JPanel(new GridLayout(1, 2));
        JButton addBtn = new JButton("Add New");
        addBtn.addActionListener(e -> { if (leftTabs.getSelectedIndex() == 0) addNewStation(); else addNewLine(); });
        JButton delBtn = new JButton("Delete");
        delBtn.setForeground(Color.RED);
        delBtn.addActionListener(e -> deleteSelected());
        leftButtonPanel.add(addBtn); leftButtonPanel.add(delBtn);
        leftPanel.add(leftButtonPanel, BorderLayout.SOUTH);
        
        // --- CENTER MAP ---
        JPanel mapContainer = new JPanel(new BorderLayout());
        mapPanel = new MapPanel();
        mapPanel.setVertexSelectionListener(v -> {
            if (v != null) {
                vertXField.setText(String.format(Locale.US, "%.2f", v[0]));
                vertZField.setText(String.format(Locale.US, "%.2f", v[1]));
                vertXField.setEnabled(true); vertZField.setEnabled(true); vertUpdateBtn.setEnabled(true);
            } else {
                vertXField.setText(""); vertZField.setText("");
                vertXField.setEnabled(false); vertZField.setEnabled(false); vertUpdateBtn.setEnabled(false);
            }
        });
        mapPanel.setStationDragListener(s -> {
            stXField.setText(String.format(Locale.US, "%.2f", s.x));
            stZField.setText(String.format(Locale.US, "%.2f", s.z));
        });
        mapContainer.add(mapPanel, BorderLayout.CENTER);
        JButton deselectBtn = new JButton("Clear Selection");
        deselectBtn.addActionListener(e -> mapPanel.clearHighlight());
        mapContainer.add(deselectBtn, BorderLayout.SOUTH);
        
        // --- RIGHT EDITOR ---
        rightEditorContainer.add(createStationEditor(), "STATION_EDIT");
        rightEditorContainer.add(createLineEditor(), "LINE_EDIT");
        
        JPanel actionPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        JButton applyBtn = new JButton("Apply Changes");
        applyBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        applyBtn.addActionListener(e -> applyChanges());
        JButton saveBtn = new JButton("Save JSON File");
        saveBtn.setBackground(new Color(144, 238, 144));
        saveBtn.addActionListener(e -> saveFile());
        actionPanel.add(applyBtn); actionPanel.add(saveBtn);
        
        JPanel rightWrapper = new JPanel(new BorderLayout());
        rightWrapper.add(rightEditorContainer, BorderLayout.CENTER);
        rightWrapper.add(actionPanel, BorderLayout.SOUTH);
        
        // --- LAYOUT ---
        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, mapContainer);
        leftSplit.setDividerLocation(250);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, rightWrapper);
        mainSplit.setDividerLocation(1050);
        add(mainSplit);
        
        // --- EVENTS ---
        leftTabs.addChangeListener(e -> {
            int idx = leftTabs.getSelectedIndex();
            rightCardLayout.show(rightEditorContainer, idx == 0 ? "STATION_EDIT" : "LINE_EDIT");
            addBtn.setText(idx == 0 ? "Add New Station" : "Add New Line");
            delBtn.setText(idx == 0 ? "Delete Station" : "Delete Line");
            editPathBtn.setSelected(false); mapPanel.setPathEditing(false);
        });
        stationList.addListSelectionListener(e -> {
            Station s = stationList.getSelectedValue();
            if (s != null && !e.getValueIsAdjusting()) {
                populateStationEditor(s);
                mapPanel.highlightStation(s, true);
            }
        });
        lineList.addListSelectionListener(e -> {
            String sel = lineList.getSelectedValue();
            if (sel != null && !e.getValueIsAdjusting()) {
                populateLineEditor(sel);
                mapPanel.highlightLine(sel, true);
            }
        });
        
        setupDragAndDrop();
        
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_RELEASED && e.getKeyCode() == KeyEvent.VK_DELETE) {
                if (mapPanel.isPathEditing() && mapPanel.hasSelectedVertex()) {
                    mapPanel.deleteSelectedVertex(); return true;
                }
            }
            return false;
        });
    }
    
    private JPanel createStationEditor() {
        JPanel main = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1.0; g.gridx = 0; g.insets = new Insets(2,10,2,10);
        
        top.add(new JLabel("Station Name:"), g); top.add(stNameField, g);
        top.add(new JLabel("Type:"), g);
        stTypeBox.addActionListener(e -> toggleElevatorFields());
        top.add(stTypeBox, g);
        
        elevatorPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        elevatorPanel.add(stY1Label); elevatorPanel.add(stY1Field);
        elevatorPanel.add(stY2Label); elevatorPanel.add(stY2Field);
        elevatorPanel.setBorder(new TitledBorder("Elevator Levels"));
        top.add(elevatorPanel, g);
        
        top.add(new JLabel("X Coord:"), g); top.add(stXField, g);
        top.add(new JLabel("Z Coord:"), g); top.add(stZField, g);
        stNotesArea.setLineWrap(true);
        top.add(new JLabel("Notes:"), g); top.add(new JScrollPane(stNotesArea), g);
        
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(new TitledBorder("Line Connections"));
        center.add(new JScrollPane(connectionTable), BorderLayout.CENTER);
        
        JPanel connBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addConn = new JButton("+ Add Connection");
        JButton remConn = new JButton("- Remove Selected");
        addConn.addActionListener(e -> connectionModel.addRow(new Object[]{"", "", "", "Main line"}));
        remConn.addActionListener(e -> {
            int row = connectionTable.getSelectedRow();
            if (row != -1) {
                if (connectionTable.isEditing()) connectionTable.getCellEditor().stopCellEditing();
                connectionModel.removeRow(row);
            }
        });
        connBtns.add(addConn); connBtns.add(remConn);
        center.add(connBtns, BorderLayout.SOUTH);
        
        main.add(top, BorderLayout.NORTH); main.add(center, BorderLayout.CENTER);
        return main;
    }
    
    private JPanel createLineEditor() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1.0; g.gridx = 0; g.insets = new Insets(5,10,5,10);
        
        p.add(new JLabel("Code:"), g); p.add(lnCodeField, g);
        p.add(new JLabel("Prefix:"), g); p.add(lnPrefixField, g);
        p.add(new JLabel("Ice Y:"), g); p.add(lnYField, g);
        
        p.add(new JLabel("Hex Color (e.g. ff0000):"), g); p.add(lnColorField, g);
        
        String[] presets = {"Presets...", "ff0000 (Red)", "00fff0 (Aqua)", "0000ff (Blue)", "00ff00 (Green)", "aa0044 (TNIH)", "ff00e3 (Monaco)"};
        JComboBox<String> psBox = new JComboBox<>(presets);
        psBox.addActionListener(e -> { String s = (String)psBox.getSelectedItem(); if (s != null && s.contains("(")) lnColorField.setText(s.split(" ")[0]); });
        p.add(psBox, g);
        
        JButton colorBtn = new JButton("Open Color Wheel...");
        colorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Select Color", colorPreview.getBackground());
            if (c != null) lnColorField.setText(String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()));
        });
        p.add(colorBtn, g);
        
        colorPreview.setPreferredSize(new Dimension(25, 25));
        colorPreview.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        p.add(new JLabel("Preview:"), g); p.add(colorPreview, g);
        
        TitledBorder pathBorder = new TitledBorder("Path Tools");
        JPanel toolPanel = new JPanel(new GridLayout(6, 1, 5, 5));
        toolPanel.setBorder(pathBorder);
        editPathBtn.addActionListener(e -> {
            mapPanel.setPathEditing(editPathBtn.isSelected());
            mapPanel.requestFocusInWindow();
        });
        toolPanel.add(editPathBtn);
        
        
        JButton resetPathBtn = new JButton("Reset Path (Revert to Saved)");
        resetPathBtn.addActionListener(e -> mapPanel.resetCurrentPath());
        toolPanel.add(resetPathBtn);
        
        JPanel vertPanel = new JPanel(new GridLayout(1, 3, 2, 0));
        vertPanel.add(new JLabel("X:")); vertPanel.add(vertXField);
        vertPanel.add(new JLabel("Z:")); vertPanel.add(vertZField);
        
        vertUpdateBtn.setEnabled(false); vertXField.setEnabled(false); vertZField.setEnabled(false);
        vertUpdateBtn.addActionListener(e -> updateSelectedVertex());
        vertXField.addActionListener(e -> updateSelectedVertex());
        vertZField.addActionListener(e -> updateSelectedVertex());
        
        toolPanel.add(new JLabel("Selected Corner:"));
        toolPanel.add(vertPanel);
        toolPanel.add(vertUpdateBtn);
        
        p.add(toolPanel, g);
        
        lnColorField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { update(); } public void removeUpdate(DocumentEvent e) { update(); } public void changedUpdate(DocumentEvent e) { update(); }
            private void update() { try { colorPreview.setBackground(Color.decode("#" + lnColorField.getText().trim())); } catch (Exception ex) { colorPreview.setBackground(Color.LIGHT_GRAY); } }
        });
        g.weighty = 1.0; p.add(new JPanel(), g);
        return p;
    }
    
    private void updateSelectedVertex() {
        try {
            double nx = Math.round(Double.parseDouble(vertXField.getText()) * 100.0) / 100.0;
            double nz = Math.round(Double.parseDouble(vertZField.getText()) * 100.0) / 100.0;
            mapPanel.updateSelectedVertexPosition(nx, nz);
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Invalid Coordinates"); }
    }
    
    private void toggleElevatorFields() {
        String t = (String) stTypeBox.getSelectedItem();
        boolean isE = t != null && t.startsWith("elev");
        stY1Field.setEnabled(isE); stY2Field.setEnabled(isE);
        stY1Label.setEnabled(isE); stY2Label.setEnabled(isE);
        elevatorPanel.setEnabled(isE);
    }
    
    private void populateStationEditor(Station s) {
        if (connectionTable.isEditing()) connectionTable.getCellEditor().stopCellEditing();
        stNameField.setText(s.name);
        stXField.setText(String.format(Locale.US, "%.2f", s.x));
        stZField.setText(String.format(Locale.US, "%.2f", s.z));
        stTypeBox.setSelectedItem(s.type == null ? "station" : s.type); stNotesArea.setText(s.notes);
        stY1Field.setText(s.y1 != null ? String.valueOf(s.y1) : "");
        stY2Field.setText(s.y2 != null ? String.valueOf(s.y2) : "");
        toggleElevatorFields();
        connectionModel.setRowCount(0);
        if (s.lines != null) s.lines.forEach((cat, lines) -> lines.forEach((ln, det) -> connectionModel.addRow(new Object[]{cat, ln, det[0], det[1]})));
    }
    
    private void populateLineEditor(String sel) {
        String[] p = sel.split(": ");
        LineData ld = data.lines.get(p[0]).get(p[1]);
        lnCodeField.setText(ld.code); lnPrefixField.setText(ld.prefix);
        lnYField.setText(String.valueOf(ld.y)); lnColorField.setText(ld.color);
        editPathBtn.setSelected(false); mapPanel.setPathEditing(false);
        vertXField.setText(""); vertZField.setText("");
        vertXField.setEnabled(false); vertZField.setEnabled(false); vertUpdateBtn.setEnabled(false);
    }
    
    private void applyChanges() {
        if (data == null) return;
        try {
            if (leftTabs.getSelectedIndex() == 0) {
                Station s = stationList.getSelectedValue(); if (s == null) return;
                if (connectionTable.isEditing()) connectionTable.getCellEditor().stopCellEditing();
                s.name = stNameField.getText();
                s.x = Math.round(Double.parseDouble(stXField.getText()) * 100.0) / 100.0;
                s.z = Math.round(Double.parseDouble(stZField.getText()) * 100.0) / 100.0;
                String t = (String)stTypeBox.getSelectedItem(); s.type = (t != null && t.equals("station")) ? null : t;
                s.notes = stNotesArea.getText().isEmpty() ? null : stNotesArea.getText();
                if (s.type != null && s.type.startsWith("elev")) {
                    s.y1 = stY1Field.getText().isEmpty() ? null : Integer.parseInt(stY1Field.getText());
                    s.y2 = stY2Field.getText().isEmpty() ? null : Integer.parseInt(stY2Field.getText());
                } else { s.y1 = null; s.y2 = null; }
                s.lines = new HashMap<>();
                for (int i = 0; i < connectionModel.getRowCount(); i++) {
                    String cat = (String)connectionModel.getValueAt(i,0); String ln = (String)connectionModel.getValueAt(i,1);
                    String brName = (String)connectionModel.getValueAt(i,3); if (brName == null || brName.isEmpty()) brName = "Main line";
                    if (cat != null && !cat.isEmpty() && ln != null && !ln.isEmpty()) {
                        s.lines.computeIfAbsent(cat, k->new HashMap<>()).put(ln, new String[]{(String)connectionModel.getValueAt(i,2), brName});
                        if (data.lines.containsKey(cat) && data.lines.get(cat).containsKey(ln)) {
                            LineData ld = data.lines.get(cat).get(ln);
                            LineData.Branch br = ld.branches.computeIfAbsent(brName, k -> { LineData.Branch b = new LineData.Branch(); b.stations = new ArrayList<>(); b.vertices = new ArrayList<>(); return b; });
                            if (!br.stations.contains(s.id)) br.stations.add(s.id);
                            boolean found = false; for (Double[] v : br.vertices) if (v[0] == s.x && v[1] == s.z) { found = true; break; }
                            if (!found) br.vertices.add(new Double[]{s.x, s.z});
                        }
                    }
                }
            } else {
                String sel = lineList.getSelectedValue(); if (sel == null) return;
                String[] p = sel.split(": "); LineData ld = data.lines.get(p[0]).get(p[1]);
                ld.code = lnCodeField.getText(); ld.prefix = lnPrefixField.getText();
                ld.y = Integer.parseInt(lnYField.getText()); ld.color = lnColorField.getText().replace("#","");
                mapPanel.commitPathChanges();
            }
            mapPanel.repaint(); JOptionPane.showMessageDialog(this, "Changes Applied Locally.");
        } catch (Exception e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, "Check inputs."); }
    }
    
    private void addNewStation() {
        if (data == null) return;
        Station s = new Station(); s.name = "New Station"; s.id = data.stations.stream().mapToInt(st -> st.id).max().orElse(0) + 1;
        s.x = mapPanel.offX; s.z = mapPanel.offZ; s.lines = new HashMap<>(); data.stations.add(s); refreshLists(); stationList.setSelectedValue(s, true);
    }
    
    private void addNewLine() {
        if (data == null) return;
        JPanel p = new JPanel(new GridLayout(2, 2, 5, 5));
        JComboBox<String> catBox = new JComboBox<>(new Vector<>(data.lines.keySet())); catBox.setEditable(true);
        JTextField nameF = new JTextField();
        p.add(new JLabel("Category:")); p.add(catBox); p.add(new JLabel("Line Name:")); p.add(nameF);
        if (JOptionPane.showConfirmDialog(this, p, "New Line", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            String cat = (String)catBox.getSelectedItem(); String name = nameF.getText();
            if (cat != null && !cat.isEmpty() && !name.isEmpty()) {
                LineData ld = new LineData(); ld.branches = new HashMap<>(); ld.color = "ffffff";
                data.lines.computeIfAbsent(cat, k -> new HashMap<>()).put(name, ld);
                refreshLists(); lineList.setSelectedValue(cat + ": " + name, true);
            }
        }
    }
    
    private void deleteSelected() {
        if (data == null) return;
        if (leftTabs.getSelectedIndex() == 0) {
            Station s = stationList.getSelectedValue();
            if (s != null && JOptionPane.showConfirmDialog(this, "Delete Station?") == 0) { data.stations.remove(s); refreshLists(); }
        } else {
            String sel = lineList.getSelectedValue();
            if (sel != null && JOptionPane.showConfirmDialog(this, "Delete Line?") == 0) {
                String[] p = sel.split(": "); data.lines.get(p[0]).remove(p[1]);
                if (data.lines.get(p[0]).isEmpty()) data.lines.remove(p[0]);
                refreshLists();
            }
        }
        mapPanel.repaint();
    }
    
    private void saveFile() {
        JFileChooser c = new JFileChooser();
        if (c.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = c.getSelectedFile(); if (!f.getName().endsWith(".json")) f = new File(f.getAbsolutePath()+".json");
            try (Writer w = new FileWriter(f)) { new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(data, w); Desktop.getDesktop().open(f.getParentFile()); } catch (Exception e) {}
        }
    }
    
    private void setupDragAndDrop() {
        this.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    java.util.List<File> files = (java.util.List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) { data = new Gson().fromJson(new FileReader(files.get(0)), HighwaysData.class); refreshLists(); mapPanel.setData(data); }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });
    }
    
    private void refreshLists() {
        stationListModel.clear(); lineListModel.clear(); if (data == null) return;
        data.stations.forEach(stationListModel::addElement);
        data.lines.forEach((cat, lines) -> lines.keySet().forEach(ln -> lineListModel.addElement(cat + ": " + ln)));
        setupTableEditors();
    }
    
    private void setupTableEditors() {
        if (data == null) return;
        JComboBox<String> catBox = new JComboBox<>(new Vector<>(data.lines.keySet())); catBox.setEditable(true);
        connectionTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(catBox));
        connectionTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<String>()) {
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                String cat = (String) table.getValueAt(row, 0);
                JComboBox<String> cb = new JComboBox<>(); cb.setEditable(true);
                if (cat != null && data.lines.containsKey(cat)) for(String l : data.lines.get(cat).keySet()) cb.addItem(l);
                else data.lines.values().forEach(m -> m.keySet().forEach(cb::addItem));
                cb.setSelectedItem(value); return cb;
            }
        });
        Set<String> allBrs = new HashSet<>(Arrays.asList("Main line"));
        data.lines.values().forEach(m -> m.values().forEach(ld -> allBrs.addAll(ld.branches.keySet())));
        JComboBox<String> brBox = new JComboBox<>(new Vector<>(allBrs)); brBox.setEditable(true);
        connectionTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(brBox));
    }
    
    private void filterLists(String q) {
        if (data == null) return;
        stationListModel.clear();
        data.stations.stream().filter(s -> s.name.toLowerCase().contains(q.toLowerCase())).forEach(stationListModel::addElement);
        lineListModel.clear();
        data.lines.forEach((cat, lines) -> lines.keySet().forEach(ln -> { String full = cat + ": " + ln; if (full.toLowerCase().contains(q.toLowerCase())) lineListModel.addElement(full); }));
    }
    
    private double roundTwoDecimals(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
    
    class MapPanel extends JPanel {
        private HighwaysData data; private double zoom = 0.05, offX = 0, offZ = 0;
        private Station highlightedStation; private String highlightedLineKey; private Point hoverPoint;
        private boolean pathEditing = false, orthogonal = true;
        private Double[] draggedVertex = null;
        private Double[] selectedVertex = null;
        private Consumer<Double[]> vertexListener;
        private Consumer<Station> stationDragListener;
        private Map<String, List<Double[]>> stagingBranches = new HashMap<>();
        
        public MapPanel() {
            setBackground(Color.WHITE);
            MouseAdapter ma = new MouseAdapter() {
                Point lastPt;
                public void mousePressed(MouseEvent e) {
                    lastPt = e.getPoint();
                    if (pathEditing && highlightedLineKey != null) {
                        if (SwingUtilities.isRightMouseButton(e)) {
                            if (selectedVertex != null && hitTestVertex(e.getX(), e.getY()) == selectedVertex) return;
                            insertVertexAt(e.getX(), e.getY());
                            return;
                        }
                        Double[] clicked = hitTestVertex(e.getX(), e.getY());
                        if (clicked != null) {
                            selectedVertex = clicked;
                            if (vertexListener != null) vertexListener.accept(selectedVertex);
                            repaint();
                            return;
                        }
                        return;
                    }
                    Object hit = findAt(e.getX(), e.getY());
                    if (hit instanceof Station s) { leftTabs.setSelectedIndex(0); stationList.setSelectedValue(s, true); }
                    else if (hit instanceof String lk) { leftTabs.setSelectedIndex(1); lineList.setSelectedValue(lk, true); }
                }
                public void mouseDragged(MouseEvent e) {
                    if (draggedVertex != null) {
                        double nx = (e.getX() - getWidth()/2) / zoom + offX; double nz = (e.getY() - getHeight()/2) / zoom + offZ;
                        if (orthogonal && highlightedLineKey != null) {
                            for (List<Double[]> verts : stagingBranches.values()) {
                                int idx = verts.indexOf(draggedVertex);
                                if (idx != -1) {
                                    Double[] prev = (idx > 0) ? verts.get(idx-1) : null;
                                    Double[] next = (idx < verts.size()-1) ? verts.get(idx+1) : null;
                                    
                                    // Smart L-Shape Snap Logic
                                    // Configuration A: Match Prev X, Match Next Z
                                    // Configuration B: Match Prev Z, Match Next X
                                    // Simple Axis Snap if only 1 neighbor
                                    
                                    if (prev != null && next != null) {
                                        // Option 1: Corner (PrevX, NextZ)
                                        double d1 = Math.hypot(nx - prev[0], nz - next[1]);
                                        // Option 2: Corner (NextX, PrevZ)
                                        double d2 = Math.hypot(nx - next[0], nz - prev[1]);
                                        
                                        if (d1 < d2) { nx = prev[0]; nz = next[1]; }
                                        else { nx = next[0]; nz = prev[1]; }
                                    } else if (prev != null) {
                                        if (Math.abs(prev[0]-nx) < Math.abs(prev[1]-nz)) nx = prev[0]; else nz = prev[1];
                                    } else if (next != null) {
                                        if (Math.abs(next[0]-nx) < Math.abs(next[1]-nz)) nx = next[0]; else nz = next[1];
                                    }
                                    break;
                                }
                            }
                        }
                        draggedVertex[0] = roundTwoDecimals(nx);
                        draggedVertex[1] = roundTwoDecimals(nz);
                        if (vertexListener != null) vertexListener.accept(draggedVertex);
                        repaint();
                    } else if (highlightedStation != null && leftTabs.getSelectedIndex() == 0 && findAt(e.getX(), e.getY()) == highlightedStation) {
                        highlightedStation.x = roundTwoDecimals(((e.getX() - getWidth()/2) / zoom + offX));
                        highlightedStation.z = roundTwoDecimals(((e.getY() - getHeight()/2) / zoom + offZ));
                        if (stationDragListener != null) stationDragListener.accept(highlightedStation);
                        repaint();
                    } else {
                        offX -= (e.getX() - lastPt.x) / zoom; offZ -= (e.getY() - lastPt.y) / zoom; lastPt = e.getPoint(); repaint();
                    }
                }
                public void mouseReleased(MouseEvent e) { draggedVertex = null; }
                public void mouseWheelMoved(MouseWheelEvent e) { double f = e.getWheelRotation() < 0 ? 1.2 : 0.8; zoom *= f; repaint(); }
                public void mouseMoved(MouseEvent e) { hoverPoint = e.getPoint(); repaint(); }
            };
            addMouseListener(ma); addMouseMotionListener(ma); addMouseWheelListener(ma);
            setFocusable(true);
        }
        public void setVertexSelectionListener(Consumer<Double[]> l) { this.vertexListener = l; }
        public void setStationDragListener(Consumer<Station> l) { this.stationDragListener = l; }
        public void setData(HighwaysData d) { this.data = d; if (!d.stations.isEmpty()) { offX = d.stations.get(0).x; offZ = d.stations.get(0).z; } repaint(); }
        public void highlightStation(Station s, boolean p) { this.highlightedStation = s; this.highlightedLineKey = null; if (p) { offX = s.x; offZ = s.z; } repaint(); }
        public void highlightLine(String l, boolean p) {
            this.highlightedLineKey = l; this.highlightedStation = null;
            stagingBranches.clear();
            String[] parts = l.split(": "); LineData ld = data.lines.get(parts[0]).get(parts[1]);
            for (Map.Entry<String, LineData.Branch> entry : ld.branches.entrySet()) {
                List<Double[]> copy = new ArrayList<>();
                for (Double[] v : entry.getValue().vertices) copy.add(new Double[]{v[0], v[1]});
                stagingBranches.put(entry.getKey(), copy);
            }
            if (p && !stagingBranches.isEmpty()) {
                List<Double[]> first = stagingBranches.values().iterator().next();
                if (!first.isEmpty()) { offX = first.get(0)[0]; offZ = first.get(0)[1]; }
            }
            repaint();
        }
        public void commitPathChanges() {
            if (highlightedLineKey == null) return;
            String[] parts = highlightedLineKey.split(": "); LineData ld = data.lines.get(parts[0]).get(parts[1]);
            for (Map.Entry<String, List<Double[]>> entry : stagingBranches.entrySet()) {
                LineData.Branch br = ld.branches.get(entry.getKey());
                if (br != null) { br.vertices.clear(); br.vertices.addAll(entry.getValue()); }
            }
        }
        public void clearHighlight() { highlightedStation = null; highlightedLineKey = null; stagingBranches.clear(); selectedVertex = null; stationList.clearSelection(); lineList.clearSelection(); pathEditing = false; repaint(); }
        public void setPathEditing(boolean b) { this.pathEditing = b; repaint(); }
        public boolean isPathEditing() { return pathEditing; }
        public boolean hasSelectedVertex() { return selectedVertex != null; }
        public void updateSelectedVertexPosition(double x, double z) { if (selectedVertex != null) { selectedVertex[0] = x; selectedVertex[1] = z; repaint(); } }
        
        public void resetCurrentPath() {
            if (highlightedLineKey == null) return;
            if (JOptionPane.showConfirmDialog(HighwayEditor.this, "Revert path to saved state?", "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            String[] parts = highlightedLineKey.split(": "); LineData ld = data.lines.get(parts[0]).get(parts[1]);
            stagingBranches.clear();
            for (Map.Entry<String, LineData.Branch> entry : ld.branches.entrySet()) {
                List<Double[]> copy = new ArrayList<>();
                for (Double[] v : entry.getValue().vertices) copy.add(new Double[]{v[0], v[1]});
                stagingBranches.put(entry.getKey(), copy);
            }
            repaint();
        }
        
        public void deleteSelectedVertex() {
            if (selectedVertex != null) {
                for (List<Double[]> verts : stagingBranches.values()) if (verts.size() > 2) verts.remove(selectedVertex);
                selectedVertex = null;
                repaint();
            }
        }
        
        private Double[] hitTestVertex(int mx, int my) {
            int cx = getWidth() / 2, cy = getHeight() / 2;
            for (List<Double[]> verts : stagingBranches.values()) for (Double[] v : verts) if (Math.hypot(mx - ((v[0]-offX)*zoom+cx), my - ((v[1]-offZ)*zoom+cy)) < 8) return v;
            return null;
        }
        private void insertVertexAt(int mx, int my) {
            int cx = getWidth()/2, cy = getHeight()/2;
            for (List<Double[]> verts : stagingBranches.values()) for (int i = 0; i < verts.size() - 1; i++) {
                double x1 = (verts.get(i)[0]-offX)*zoom+cx, z1 = (verts.get(i)[1]-offZ)*zoom+cy;
                double x2 = (verts.get(i+1)[0]-offX)*zoom+cx, z2 = (verts.get(i+1)[1]-offZ)*zoom+cy;
                if (Line2D.ptSegDist(x1, z1, x2, z2, mx, my) < 5) {
                    double px = (mx-cx)/zoom+offX, pz = (my-cy)/zoom+offZ;
                    double v1x = verts.get(i)[0], v1z = verts.get(i)[1];
                    double v2x = verts.get(i+1)[0], v2z = verts.get(i+1)[1];
                    double dx = v2x - v1x, dz = v2z - v1z;
                    double t = ((px-v1x)*dx + (pz-v1z)*dz) / (dx*dx + dz*dz);
                    t = Math.max(0, Math.min(1, t));
                    double nx = roundTwoDecimals(v1x + t * dx);
                    double nz = roundTwoDecimals(v1z + t * dz);
                    verts.add(i + 1, new Double[]{nx, nz});
                    repaint(); return;
                }
            }
        }
        private void deleteVertexAt(int mx, int my) {
            Double[] v = hitTestVertex(mx, my);
            if (v != null) { for (List<Double[]> verts : stagingBranches.values()) if (verts.size() > 2) { verts.remove(v); if(v==selectedVertex) selectedVertex=null; } repaint(); }
        }
        private Object findAt(int mx, int my) {
            if (data == null) return null;
            int cx = getWidth()/2, cy = getHeight()/2;
            for (Station s : data.stations) if (Math.hypot(mx - ((s.x-offX)*zoom+cx), my - ((s.z-offZ)*zoom+cy)) < 15) return s;
            for (Map.Entry<String, Map<String, LineData>> cat : data.lines.entrySet()) for (Map.Entry<String, LineData> entry : cat.getValue().entrySet()) for (LineData.Branch br : entry.getValue().branches.values()) for (int i = 0; i < br.vertices.size() - 1; i++) {
                double x1 = (br.vertices.get(i)[0]-offX)*zoom+cx, z1 = (br.vertices.get(i)[1]-offZ)*zoom+cy;
                double x2 = (br.vertices.get(i+1)[0]-offX)*zoom+cx, z2 = (br.vertices.get(i+1)[1]-offZ)*zoom+cy;
                if (Line2D.ptSegDist(x1, z1, x2, z2, mx, my) < 5) return cat.getKey() + ": " + entry.getKey();
            }
            return null;
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g); if (data == null) return;
            Graphics2D g2 = (Graphics2D) g; g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cx = getWidth()/2, cy = getHeight()/2;
            data.lines.forEach((cat, lines) -> lines.forEach((name, line) -> {
                boolean high = (highlightedLineKey != null && highlightedLineKey.equals(cat + ": " + name));
                boolean rel = high || (highlightedStation != null && highlightedStation.lines != null && highlightedStation.lines.containsKey(cat) && highlightedStation.lines.get(cat).containsKey(name));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (highlightedStation == null && highlightedLineKey == null) ? 1.0f : (rel ? 1.0f : 0.15f)));
                Color c = Color.BLACK; try { c = Color.decode("#" + line.color); } catch(Exception e){}
                if (high && !stagingBranches.isEmpty()) {
                    for (List<Double[]> verts : stagingBranches.values()) {
                        drawPath(g2, verts, c, cx, cy);
                        if (pathEditing) {
                            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                            for (Double[] v : verts) {
                                int vx = (int)((v[0]-offX)*zoom+cx), vz = (int)((v[1]-offZ)*zoom+cy);
                                g2.setColor(v == selectedVertex || v == draggedVertex ? Color.RED : Color.BLUE); g2.fillRect(vx-4, vz-4, 8, 8);
                            }
                        }
                    }
                } else { for (LineData.Branch br : line.branches.values()) drawPath(g2, br.vertices, c, cx, cy); }
            }));
            for (Station s : data.stations) {
                boolean isSelected = (highlightedStation != null && highlightedStation.id == s.id);
                boolean rel = isSelected || (highlightedLineKey != null && s.lines != null && s.lines.containsKey(highlightedLineKey.split(": ")[0]) && s.lines.get(highlightedLineKey.split(": ")[0]).containsKey(highlightedLineKey.split(": ")[1]));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (highlightedStation == null && highlightedLineKey == null) ? 1.0f : (rel ? 1.0f : 0.15f)));
                drawStationIcon(g2, s, cx, cy);
                if (isSelected || (hoverPoint != null && Math.hypot((s.x-offX)*zoom+cx - hoverPoint.x, (s.z-offZ)*zoom+cy - hoverPoint.y) < 12)) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); drawTextWithContour(g2, s.name, (int)((s.x-offX)*zoom+cx)+12, (int)((s.z-offZ)*zoom+cy)+5);
                }
            }
        }
        private void drawTextWithContour(Graphics2D g2, String t, int x, int y) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 12)); g2.setColor(Color.WHITE);
            for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) if (i != 0 || j != 0) g2.drawString(t, x + i, y + j);
            g2.setColor(Color.BLACK); g2.drawString(t, x, y);
        }
        private void drawPath(Graphics2D g2, List<Double[]> v, Color c, int cx, int cy) {
            if (v.size() < 2) return;
            Path2D path = new Path2D.Double(); path.moveTo((v.get(0)[0]-offX)*zoom+cx, (v.get(0)[1]-offZ)*zoom+cy);
            for (int i = 1; i < v.size(); i++) path.lineTo((v.get(i)[0]-offX)*zoom+cx, (v.get(i)[1]-offZ)*zoom+cy);
            g2.setColor(Color.BLACK); g2.setStroke(new BasicStroke((float)(8*zoom+5), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g2.draw(path);
            g2.setColor(c); g2.setStroke(new BasicStroke((float)(4*zoom+3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g2.draw(path);
        }
        private void drawStationIcon(Graphics2D g2, Station s, int cx, int cy) {
            int x = (int)((s.x-offX)*zoom+cx), z = (int)((s.z-offZ)*zoom+cy);
            int sz = (int)Math.min(Math.max(10, 15*zoom*10), 20); g2.setColor(Color.BLACK);
            if (s.type != null && (s.type.contains("jct") || s.type.contains("inter"))) { g2.fillRect(x-sz/2, z-sz/2, sz, sz); g2.setColor(Color.WHITE); g2.fillRect(x-sz/2+2, z-sz/2+2, sz-4, sz-4); }
            else if (s.type != null && s.type.contains("elev")) { g2.setStroke(new BasicStroke(3)); g2.drawLine(x-sz/2, z+sz/2, x-sz/2, z); g2.drawLine(x-sz/2, z, x, z); g2.drawLine(x, z, x, z-sz/2); g2.drawLine(x, z-sz/2, x+sz/2, z-sz/2); }
            else { g2.fillOval(x-sz/2, z-sz/2, sz, sz); g2.setColor(Color.WHITE); g2.fillOval(x-sz/2+2, z-sz/2+2, sz-4, sz-4); }
        }
    }
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new HighwayEditor().setVisible(true)); }
}