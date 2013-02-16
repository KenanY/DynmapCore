package org.dynmap.markers.impl;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.Event;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.Client.ComponentMessage;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerIcon.MarkerSize;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PlayerSet;
import org.dynmap.markers.PolyLineMarker;
import org.dynmap.web.Json;

/**
 * Implementation class for MarkerAPI - should not be called directly
 */
public class MarkerAPIImpl implements MarkerAPI, Event.Listener<DynmapWorld> {
    private File markerpersist;
    private File markerpersist_old;
    private File markerdir; /* Local store for markers (internal) */
    private File markertiledir; /* Marker directory for web server (under tiles) */
    private HashMap<String, MarkerIconImpl> markericons = new HashMap<String, MarkerIconImpl>();
    private HashMap<String, MarkerSetImpl> markersets = new HashMap<String, MarkerSetImpl>();
    private HashMap<String, List<DynmapLocation>> pointaccum = new HashMap<String, List<DynmapLocation>>();
    private HashMap<String, PlayerSetImpl> playersets = new HashMap<String, PlayerSetImpl>();
    private DynmapCore core;
    static MarkerAPIImpl api;

    /* Built-in icons */
    private static final String[] builtin_icons = {
        "anchor", "bank", "basket", "bed", "beer", "bighouse", "blueflag", "bomb", "bookshelf", "bricks", "bronzemedal", "bronzestar",
        "building", "cake", "camera", "cart", "caution", "chest", "church", "coins", "comment", "compass", "construction",
        "cross", "cup", "cutlery", "default", "diamond", "dog", "door", "down", "drink", "exclamation", "factory",
        "fire", "flower", "gear", "goldmedal", "goldstar", "greenflag", "hammer", "heart", "house", "key", "king",
        "left", "lightbulb", "lighthouse", "lock", "minecart", "orangeflag", "pin", "pinkflag", "pirateflag", "pointdown", "pointleft",
        "pointright", "pointup", "portal", "purpleflag", "queen", "redflag", "right", "ruby", "scales", "skull", "shield", "sign",
        "silvermedal", "silverstar", "star", "sun", "temple", "theater", "tornado", "tower", "tree", "truck", "up",
        "walk", "warning", "world", "wrench", "yellowflag", "offlineuser"
    };

    /* Component messages for client updates */
    public static class MarkerComponentMessage extends ComponentMessage {
        public String ctype = "markers";
    }
    
    public static class MarkerUpdated extends MarkerComponentMessage {
        public String msg;
        public double x, y, z;
        public String id;
        public String label;
        public String icon;
        public String set;
        public boolean markup;
        public String desc;
        public String dim;
        
        public MarkerUpdated(Marker m, boolean deleted) {
            this.id = m.getMarkerID();
            this.label = m.getLabel();
            this.x = m.getX();
            this.y = m.getY();
            this.z = m.getZ();
            this.set = m.getMarkerSet().getMarkerSetID();
            this.icon = m.getMarkerIcon().getMarkerIconID();
            this.markup = m.isLabelMarkup();
            this.desc = m.getDescription();
            this.dim = m.getMarkerIcon().getMarkerIconSize().getSize();
            if(deleted) 
                msg = "markerdeleted";
            else
                msg = "markerupdated";
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof MarkerUpdated) {
                MarkerUpdated m = (MarkerUpdated)o;
                return m.id.equals(id) && m.set.equals(set);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return id.hashCode() ^ set.hashCode();
        }

    }

    public static class AreaMarkerUpdated extends MarkerComponentMessage {
        public String msg;
        public double ytop, ybottom;
        public double[] x;
        public double[] z;
        public int weight;
        public double opacity;
        public String color;
        public double fillopacity;
        public String fillcolor;
        public String id;
        public String label;
        public String set;
        public String desc;
        
        public AreaMarkerUpdated(AreaMarker m, boolean deleted) {
            this.id = m.getMarkerID();
            this.label = m.getLabel();
            this.ytop = m.getTopY();
            this.ybottom = m.getBottomY();
            int cnt = m.getCornerCount();
            x = new double[cnt];
            z = new double[cnt];
            for(int i = 0; i < cnt; i++) {
                x[i] = m.getCornerX(i);
                z[i] = m.getCornerZ(i);
            }
            color = String.format("#%06X", m.getLineColor());
            weight = m.getLineWeight();
            opacity = m.getLineOpacity();
            fillcolor = String.format("#%06X", m.getFillColor());
            fillopacity = m.getFillOpacity();
            desc = m.getDescription();
            
            this.set = m.getMarkerSet().getMarkerSetID();
            if(deleted) 
                msg = "areadeleted";
            else
                msg = "areaupdated";
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof AreaMarkerUpdated) {
                AreaMarkerUpdated m = (AreaMarkerUpdated)o;
                return m.id.equals(id) && m.set.equals(set);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return id.hashCode() ^ set.hashCode();
        }
    }

    public static class PolyLineMarkerUpdated extends MarkerComponentMessage {
        public String msg;
        public double[] x;
        public double[] y;
        public double[] z;
        public int weight;
        public double opacity;
        public String color;
        public String id;
        public String label;
        public String set;
        public String desc;
        
        public PolyLineMarkerUpdated(PolyLineMarker m, boolean deleted) {
            this.id = m.getMarkerID();
            this.label = m.getLabel();
            int cnt = m.getCornerCount();
            x = new double[cnt];
            y = new double[cnt];
            z = new double[cnt];
            for(int i = 0; i < cnt; i++) {
                x[i] = m.getCornerX(i);
                y[i] = m.getCornerY(i);
                z[i] = m.getCornerZ(i);
            }
            color = String.format("#%06X", m.getLineColor());
            weight = m.getLineWeight();
            opacity = m.getLineOpacity();
            desc = m.getDescription();
            
            this.set = m.getMarkerSet().getMarkerSetID();
            if(deleted) 
                msg = "polydeleted";
            else
                msg = "polyupdated";
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof PolyLineMarkerUpdated) {
                PolyLineMarkerUpdated m = (PolyLineMarkerUpdated)o;
                return m.id.equals(id) && m.set.equals(set);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return id.hashCode() ^ set.hashCode();
        }
    }

    public static class CircleMarkerUpdated extends MarkerComponentMessage {
        public String msg;
        public double x;
        public double y;
        public double z;
        public double xr;
        public double zr;
        public int weight;
        public double opacity;
        public String color;
        public double fillopacity;
        public String fillcolor;
        public String id;
        public String label;
        public String set;
        public String desc;
        
        public CircleMarkerUpdated(CircleMarker m, boolean deleted) {
            this.id = m.getMarkerID();
            this.label = m.getLabel();
            this.x = m.getCenterX();
            this.y = m.getCenterY();
            this.z = m.getCenterZ();
            this.xr = m.getRadiusX();
            this.zr = m.getRadiusZ();
            color = String.format("#%06X", m.getLineColor());
            weight = m.getLineWeight();
            opacity = m.getLineOpacity();
            fillcolor = String.format("#%06X", m.getFillColor());
            fillopacity = m.getFillOpacity();
            desc = m.getDescription();
            
            this.set = m.getMarkerSet().getMarkerSetID();
            if(deleted) 
                msg = "circledeleted";
            else
                msg = "circleupdated";
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof CircleMarkerUpdated) {
                CircleMarkerUpdated m = (CircleMarkerUpdated)o;
                return m.id.equals(id) && m.set.equals(set);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return id.hashCode() ^ set.hashCode();
        }
    }

    public static class MarkerSetUpdated extends MarkerComponentMessage {
        public String msg;
        public String id;
        public String label;
        public int layerprio;
        public int minzoom;
        public Boolean showlabels;
        public MarkerSetUpdated(MarkerSet markerset, boolean deleted) {
            this.id = markerset.getMarkerSetID();
            this.label = markerset.getMarkerSetLabel();
            this.layerprio = markerset.getLayerPriority();
            this.minzoom = markerset.getMinZoom();
            this.showlabels = markerset.getLabelShow();
            if(deleted)
                msg = "setdeleted";
            else
                msg = "setupdated";
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof MarkerSetUpdated) {
                MarkerSetUpdated m = (MarkerSetUpdated)o;
                return m.id.equals(id);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
    
    private boolean stop = false;
    private Set<String> dirty_worlds = new HashSet<String>();
    private boolean dirty_markers = false;
    
    private class DoFileWrites implements Runnable {
        public void run() {
            if(stop)
                return;
            /* Write markers first - drives JSON updates too */
            if(dirty_markers) {
                doSaveMarkers();
                dirty_markers = false;
            }
            /* Process any dirty worlds */
            if(!dirty_worlds.isEmpty()) {
                for(String world : dirty_worlds) {
                    writeMarkersFile(world);
                }
                dirty_worlds.clear();
            }
            core.getServer().scheduleServerTask(this, 20);
        }
    }

    /**
     * Singleton initializer
     */
    public static MarkerAPIImpl initializeMarkerAPI(DynmapCore core) {
        if(api != null) {
            api.cleanup(core);
        }
        api = new MarkerAPIImpl();
        api.core = core;
        /* Initialize persistence file name */
        api.markerpersist = new File(core.getDataFolder(), "markers.yml");
        api.markerpersist_old = new File(core.getDataFolder(), "markers.yml.old");
        /* Fill in default icons and sets, if needed */
        for(int i = 0; i < builtin_icons.length; i++) {
            String id = builtin_icons[i];
            api.createBuiltinMarkerIcon(id, id);
        }
        /* Load persistence */
        api.loadMarkers();
        /* Initialize default marker set, if needed */
        MarkerSet set = api.getMarkerSet(MarkerSet.DEFAULT);
        if(set == null) {
            set = api.createMarkerSet(MarkerSet.DEFAULT, "Markers", null, true);
        }
        
        /* Build paths for markers */
        api.markerdir = new File(core.getDataFolder(), "markers");
        if(api.markerdir.isDirectory() == false) {
            if(api.markerdir.mkdirs() == false) {   /* Create directory if needed */
                Log.severe("Error creating markers directory - " + api.markerdir.getPath());
            }
        }
        api.markertiledir = new File(core.getTilesFolder(), "_markers_");
        if(api.markertiledir.isDirectory() == false) {
            if(api.markertiledir.mkdirs() == false) {   /* Create directory if needed */
                Log.severe("Error creating markers directory - " + api.markertiledir.getPath());
            }
        }
        /* Now publish marker files to the tiles directory */
        for(MarkerIcon ico : api.getMarkerIcons()) {
            api.publishMarkerIcon(ico);
        }
        /* Freshen files */
        api.freshenMarkerFiles();
        /* Add listener so we update marker files for other worlds as they become active */
        core.events.addListener("worldactivated", api);

        api.scheduleWriteJob(); /* Start write job */
        
        return api;
    }
    
    private void scheduleWriteJob() {
        core.getServer().scheduleServerTask(new DoFileWrites(), 20);
    }
    
    /**
     * Cleanup
     */
    public void cleanup(DynmapCore plugin) {
        plugin.events.removeListener("worldactivated", api);

        stop = true;
        if(dirty_markers) {
            doSaveMarkers();
            dirty_markers = false;
        }
        
        for(MarkerIconImpl icn : markericons.values())
            icn.cleanup();
        markericons.clear();
        for(MarkerSetImpl set : markersets.values())
            set.cleanup();
        markersets.clear();
    }

    private MarkerIcon createBuiltinMarkerIcon(String id, String label) {
        if(markericons.containsKey(id)) return null;    /* Exists? */
        MarkerIconImpl ico = new MarkerIconImpl(id, label, true);
        markericons.put(id, ico);   /* Add to set */
        return ico;
    }

    void publishMarkerIcon(MarkerIcon ico) {
        byte[] buf = new byte[512];
        InputStream in = null;
        File infile = new File(markerdir, ico.getMarkerIconID() + ".png");  /* Get source file name */
        File outfile = new File(markertiledir, ico.getMarkerIconID() + ".png"); /* Destination */
        BufferedImage im = null;
        OutputStream out = null;
                
        try {
            out = new FileOutputStream(outfile);
        } catch (IOException iox) {
            Log.severe("Cannot write marker to tilespath - " + outfile.getPath());
            return;
        }
        if(ico.isBuiltIn()) {
            in = getClass().getResourceAsStream("/markers/" + ico.getMarkerIconID() + ".png");
        }
        else if(infile.canRead()) {  /* If it exists and is readable */
            try {
                im = ImageIO.read(infile);
            } catch (IOException e) {
            } catch (IndexOutOfBoundsException e) {
            }
            if(im != null) {
                MarkerIconImpl icon = (MarkerIconImpl)ico;
                int w = im.getWidth();  /* Get width */
                if(w <= 8) {    /* Small size? */
                    icon.setMarkerIconSize(MarkerSize.MARKER_8x8);
                }
                else if(w > 16) {
                    icon.setMarkerIconSize(MarkerSize.MARKER_32x32);
                }
                else {
                    icon.setMarkerIconSize(MarkerSize.MARKER_16x16);
                }
                im.flush();
            }

            try {
                in = new FileInputStream(infile);   
            } catch (IOException iox) {
                Log.severe("Error opening marker " + infile.getPath() + " - " + iox);
            }
        }
        if(in == null) {    /* Not found, use default marker */
            in = getClass().getResourceAsStream("/markers/marker.png");
            if(in == null)
                return;
        }
        /* Copy to destination */
        try {
            int len;
            while((len = in.read(buf)) > 0) {
               out.write(buf, 0, len); 
            }
        } catch (IOException iox) {
            Log.severe("Error writing marker to tilespath - " + outfile.getPath());
        } finally {
            if(in != null) try { in.close(); } catch (IOException x){}
            if(out != null) try { out.close(); } catch (IOException x){}
        }
    }
    
    @Override
    public Set<MarkerSet> getMarkerSets() {
        return new HashSet<MarkerSet>(markersets.values());
    }

    @Override
    public MarkerSet getMarkerSet(String id) {
        return markersets.get(id);
    }

    @Override
    public MarkerSet createMarkerSet(String id, String lbl, Set<MarkerIcon> iconlimit, boolean persistent) {
        if(markersets.containsKey(id)) return null; /* Exists? */
        
        MarkerSetImpl set = new MarkerSetImpl(id, lbl, iconlimit, persistent);

        markersets.put(id, set);    /* Add to list */
        if(persistent) {
            saveMarkers();
        }
        markerSetUpdated(set, MarkerUpdate.CREATED); /* Notify update */
        
        return set;
    }

    @Override
    public Set<MarkerIcon> getMarkerIcons() {
        return new HashSet<MarkerIcon>(markericons.values());
    }

    @Override
    public MarkerIcon getMarkerIcon(String id) {
        return markericons.get(id);
    }

    boolean loadMarkerIconStream(String id, InputStream in) {
        /* Copy icon resource into marker directory */
        File f = new File(markerdir, id + ".png");
        FileOutputStream fos = null;
        try {
            byte[] buf = new byte[512];
            int len;
            fos = new FileOutputStream(f);
            while((len = in.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
        } catch (IOException iox) {
            Log.severe("Error copying marker - " + f.getPath());
            return false;
        } finally {
            if(fos != null) try { fos.close(); } catch (IOException x) {}
        }
        return true;
    }
    @Override
    public MarkerIcon createMarkerIcon(String id, String label, InputStream marker_png) {
        if(markericons.containsKey(id)) return null;    /* Exists? */
        MarkerIconImpl ico = new MarkerIconImpl(id, label, false);
        /* Copy icon resource into marker directory */
        if(!loadMarkerIconStream(id, marker_png))
            return null;
        markericons.put(id, ico);   /* Add to set */

        /* Publish the marker */
        publishMarkerIcon(ico);
        
        saveMarkers();  /* Save results */
        
        return ico;
    }

    static MarkerIconImpl getMarkerIconImpl(String id) {
        if(api != null) {
            return api.markericons.get(id);
        }
        return null;
    }

    @Override
    public Set<PlayerSet> getPlayerSets() {
        return new HashSet<PlayerSet>(playersets.values());
    }

    @Override
    public PlayerSet getPlayerSet(String id) {
        return playersets.get(id);
    }

    @Override
    public PlayerSet createPlayerSet(String id, boolean symmetric, Set<String> players, boolean persistent) {
        if(playersets.containsKey(id)) return null; /* Exists? */
        
        PlayerSetImpl set = new PlayerSetImpl(id, symmetric, players, persistent);

        playersets.put(id, set);    /* Add to list */
        if(persistent) {
            saveMarkers();
        }
        playerSetUpdated(set, MarkerUpdate.CREATED); /* Notify update */
        
        return set;
    }

    /**
     * Save persistence for markers
     */
    static void saveMarkers() {
        if(api != null) {
            api.dirty_markers = true;
        }
    }
    
    private void doSaveMarkers() {
        if(api != null) {
            ConfigurationNode conf = new ConfigurationNode(api.markerpersist);  /* Make configuration object */
            /* First, save icon definitions */
            HashMap<String, Object> icons = new HashMap<String,Object>();
            for(String id : api.markericons.keySet()) {
                MarkerIconImpl ico = api.markericons.get(id);
                Map<String,Object> dat = ico.getPersistentData();
                if(dat != null) {
                    icons.put(id, dat);
                }
            }
            conf.put("icons", icons);
            /* Then, save persistent sets */
            HashMap<String, Object> sets = new HashMap<String, Object>();
            for(String id : api.markersets.keySet()) {
                MarkerSetImpl set = api.markersets.get(id);
                if(set.isMarkerSetPersistent()) {
                    Map<String, Object> dat = set.getPersistentData();
                    if(dat != null) {
                        sets.put(id, dat);
                    }
                }
            }
            conf.put("sets", sets);
            /* Then, save persistent player sets */
            HashMap<String, Object> psets = new HashMap<String, Object>();
            for(String id : api.playersets.keySet()) {
                PlayerSetImpl set = api.playersets.get(id);
                if(set.isPersistentSet()) {
                    Map<String, Object> dat = set.getPersistentData();
                    if(dat != null) {
                        psets.put(id, dat);
                    }
                }
            }
            conf.put("playersets", psets);
            /* And shift old file file out */
            if(api.markerpersist_old.exists()) api.markerpersist_old.delete();
            if(api.markerpersist.exists()) api.markerpersist.renameTo(api.markerpersist_old);
            /* And write it out */
            if(!conf.save())
                Log.severe("Error writing markers - " + api.markerpersist.getPath());
            /* Refresh JSON files */
            api.freshenMarkerFiles();
        }
    }

    private void freshenMarkerFiles() {
        if(MapManager.mapman != null) {
            for(DynmapWorld w : MapManager.mapman.worlds) {
                dirty_worlds.add(w.getName());
            }
        }
    }
    
    /**
     * Load persistence
     */
    private boolean loadMarkers() {        
        ConfigurationNode conf = new ConfigurationNode(api.markerpersist);  /* Make configuration object */
        conf.load();    /* Load persistence */
        /* Get icons */
        
        ConfigurationNode icons = conf.getNode("icons");
        if(icons == null) return false;
        for(String id : icons.keySet()) {
            MarkerIconImpl ico = new MarkerIconImpl(id);
            if(ico.loadPersistentData(icons.getNode(id))) {
                markericons.put(id, ico);
            }
        }
        /* Get marker sets */
        ConfigurationNode sets = conf.getNode("sets");
        if(sets != null) {
            for(String id: sets.keySet()) {
                MarkerSetImpl set = new MarkerSetImpl(id);
                if(set.loadPersistentData(sets.getNode(id))) {
                    markersets.put(id, set);
                }
            }
        }
        /* Get player sets */
        ConfigurationNode psets = conf.getNode("playersets");
        if(psets != null) {
            for(String id: psets.keySet()) {
                PlayerSetImpl set = new PlayerSetImpl(id);
                if(set.loadPersistentData(sets.getNode(id))) {
                    playersets.put(id, set);
                }
            }
        }
        
        return true;
    }
    
    enum MarkerUpdate { CREATED, UPDATED, DELETED };
    
    /**
     * Signal marker update
     * @param marker - updated marker
     * @param update - type of update
     */
    static void markerUpdated(MarkerImpl marker, MarkerUpdate update) {
        /* Freshen marker file for the world for this marker */
        if(api != null)
            api.dirty_worlds.add(marker.getNormalizedWorld());
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(marker.getNormalizedWorld(), new MarkerUpdated(marker, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal area marker update
     * @param marker - updated marker
     * @param update - type of update
     */
    static void areaMarkerUpdated(AreaMarkerImpl marker, MarkerUpdate update) {
        /* Freshen marker file for the world for this marker */
        if(api != null)
            api.dirty_worlds.add(marker.getNormalizedWorld());
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(marker.getNormalizedWorld(), new AreaMarkerUpdated(marker, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal poly-line marker update
     * @param marker - updated marker
     * @param update - type of update
     */
    static void polyLineMarkerUpdated(PolyLineMarkerImpl marker, MarkerUpdate update) {
        /* Freshen marker file for the world for this marker */
        if(api != null)
            api.dirty_worlds.add(marker.getNormalizedWorld());
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(marker.getNormalizedWorld(), new PolyLineMarkerUpdated(marker, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal circle marker update
     * @param marker - updated marker
     * @param update - type of update
     */
    static void circleMarkerUpdated(CircleMarkerImpl marker, MarkerUpdate update) {
        /* Freshen marker file for the world for this marker */
        if(api != null)
            api.dirty_worlds.add(marker.getNormalizedWorld());
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(marker.getNormalizedWorld(), new CircleMarkerUpdated(marker, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal marker set update
     * @param markerset - updated marker set
     * @param update - type of update
     */
    static void markerSetUpdated(MarkerSetImpl markerset, MarkerUpdate update) {
        /* Freshen all marker files */
        if(api != null)
            api.freshenMarkerFiles();
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(new MarkerSetUpdated(markerset, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal player set update
     * @param playerset - updated player set
     * @param update - type of update
     */
    static void playerSetUpdated(PlayerSetImpl pset, MarkerUpdate update) {
        if(api != null)
            api.core.events.trigger("playersetupdated", null);
    }
    
    /**
     * Remove marker set
     */
    static void removeMarkerSet(MarkerSetImpl markerset) {
        if(api != null) {
            api.markersets.remove(markerset.getMarkerSetID());  /* Remove set from list */
            if(markerset.isMarkerSetPersistent()) {   /* If persistent */
                MarkerAPIImpl.saveMarkers();        /* Drive save */
            }
            markerSetUpdated(markerset, MarkerUpdate.DELETED); /* Signal delete of set */
        }
    }

    /**
     * Remove player set
     */
    static void removePlayerSet(PlayerSetImpl pset) {
        if(api != null) {
            api.playersets.remove(pset.getSetID());  /* Remove set from list */
            if(pset.isPersistentSet()) {   /* If persistent */
                MarkerAPIImpl.saveMarkers();        /* Drive save */
            }
            playerSetUpdated(pset, MarkerUpdate.DELETED); /* Signal delete of set */
        }
    }

    private static boolean processAreaArgs(DynmapCommandSender sender, AreaMarker marker, Map<String,String> parms) {
        String val = null;
        try {
            double ytop = marker.getTopY();
            double ybottom = marker.getBottomY();
            int scolor = marker.getLineColor();
            int fcolor = marker.getFillColor();
            double sopacity = marker.getLineOpacity();
            double fopacity = marker.getFillOpacity();
            int sweight = marker.getLineWeight();

            val = parms.get(ARG_STROKECOLOR);
            if(val != null)
                scolor = Integer.parseInt(val, 16);
            val = parms.get(ARG_FILLCOLOR);
            if(val != null)
                fcolor = Integer.parseInt(val, 16);
            val = parms.get(ARG_STROKEOPACITY);
            if(val != null)
                sopacity = Double.parseDouble(val);
            val = parms.get(ARG_FILLOPACITY);
            if(val != null)
                fopacity = Double.parseDouble(val);
            val = parms.get(ARG_STROKEWEIGHT);
            if(val != null)
                sweight = Integer.parseInt(val);
            val = parms.get(ARG_YTOP);
            if(val != null)
                ytop = Double.parseDouble(val);
            val = parms.get(ARG_YBOTTOM);
            if(val != null)
                ybottom = Double.parseDouble(val);
            marker.setLineStyle(sweight, sopacity, scolor);
            marker.setFillStyle(fopacity, fcolor);
            if(ytop >= ybottom)
                marker.setRangeY(ytop, ybottom);
            else
                marker.setRangeY(ybottom, ytop);
        } catch (NumberFormatException nfx) {
            sender.sendMessage("Invalid parameter format: " + val);
            return false;
        }
        return true;
    }

    private static boolean processPolyArgs(DynmapCommandSender sender, PolyLineMarker marker, Map<String,String> parms) {
        String val = null;
        try {
            int scolor = marker.getLineColor();
            double sopacity = marker.getLineOpacity();
            int sweight = marker.getLineWeight();

            val = parms.get(ARG_STROKECOLOR);
            if(val != null)
                scolor = Integer.parseInt(val, 16);
            val = parms.get(ARG_STROKEOPACITY);
            if(val != null)
                sopacity = Double.parseDouble(val);
            val = parms.get(ARG_STROKEWEIGHT);
            if(val != null)
                sweight = Integer.parseInt(val);
            marker.setLineStyle(sweight, sopacity, scolor);
        } catch (NumberFormatException nfx) {
            sender.sendMessage("Invalid parameter format: " + val);
            return false;
        }
        return true;
    }

    private static boolean processCircleArgs(DynmapCommandSender sender, CircleMarker marker, Map<String,String> parms) {
        String val = null;
        try {
            int scolor = marker.getLineColor();
            int fcolor = marker.getFillColor();
            double sopacity = marker.getLineOpacity();
            double fopacity = marker.getFillOpacity();
            int sweight = marker.getLineWeight();
            double xr = marker.getRadiusX();
            double zr = marker.getRadiusZ();
            double x = marker.getCenterX();
            double y = marker.getCenterY();
            double z = marker.getCenterZ();
            String world = marker.getWorld();
            
            val = parms.get(ARG_STROKECOLOR);
            if(val != null)
                scolor = Integer.parseInt(val, 16);
            val = parms.get(ARG_FILLCOLOR);
            if(val != null)
                fcolor = Integer.parseInt(val, 16);
            val = parms.get(ARG_STROKEOPACITY);
            if(val != null)
                sopacity = Double.parseDouble(val);
            val = parms.get(ARG_FILLOPACITY);
            if(val != null)
                fopacity = Double.parseDouble(val);
            val = parms.get(ARG_STROKEWEIGHT);
            if(val != null)
                sweight = Integer.parseInt(val);
            val = parms.get(ARG_X);
            if(val != null)
                x = Double.parseDouble(val);
            val = parms.get(ARG_Y);
            if(val != null)
                y = Double.parseDouble(val);
            val = parms.get(ARG_Z);
            if(val != null)
                z = Double.parseDouble(val);
            val = parms.get(ARG_WORLD);
            if(val != null)
                world = val;
            val = parms.get(ARG_RADIUSX);
            if(val != null)
                xr = Double.parseDouble(val);
            val = parms.get(ARG_RADIUSZ);
            if(val != null)
                zr = Double.parseDouble(val);
            val = parms.get(ARG_RADIUS);
            if(val != null)
                xr = zr = Double.parseDouble(val);
            marker.setCenter(world, x, y, z);
            marker.setLineStyle(sweight, sopacity, scolor);
            marker.setFillStyle(fopacity, fcolor);
            marker.setRadius(xr, zr);
        } catch (NumberFormatException nfx) {
            sender.sendMessage("Invalid parameter format: " + val);
            return false;
        }
        return true;
    }

    private static final Set<String> commands = new HashSet<String>(Arrays.asList(new String[] {
        "add", "movehere", "update", "delete", "list", "icons", "addset", "updateset", "deleteset", "listsets", "addicon", "updateicon",
        "deleteicon", "addcorner", "clearcorners", "addarea", "listareas", "deletearea", "updatearea",
        "addline", "listlines", "deleteline", "updateline", "addcircle", "listcircles", "deletecircle", "updatecircle"
    }));
    private static final String ARG_LABEL = "label";
    private static final String ARG_ID = "id";
    private static final String ARG_NEWLABEL = "newlabel";
    private static final String ARG_FILE = "file";
    private static final String ARG_HIDE = "hide";
    private static final String ARG_ICON = "icon";
    private static final String ARG_DEFICON = "deficon";
    private static final String ARG_SET = "set";
    private static final String ARG_NEWSET = "newset";
    private static final String ARG_PRIO = "prio";
    private static final String ARG_MINZOOM = "minzoom";
    private static final String ARG_STROKEWEIGHT = "weight";
    private static final String ARG_STROKECOLOR = "color";
    private static final String ARG_STROKEOPACITY = "opacity";
    private static final String ARG_FILLCOLOR = "fillcolor";
    private static final String ARG_FILLOPACITY = "fillopacity";
    private static final String ARG_YTOP = "ytop";
    private static final String ARG_YBOTTOM = "ybottom";
    private static final String ARG_RADIUSX = "radiusx";
    private static final String ARG_RADIUSZ = "radiusz";
    private static final String ARG_RADIUS = "radius";
    private static final String ARG_SHOWLABEL = "showlabels";
    private static final String ARG_X = "x";
    private static final String ARG_Y = "y";
    private static final String ARG_Z = "z";
    private static final String ARG_WORLD = "world";
    
    
    /* Parse argument strings : handle 'attrib:value' and quoted strings */
    private static Map<String,String> parseArgs(String[] args, DynmapCommandSender snd) {
        HashMap<String,String> rslt = new HashMap<String,String>();
        /* Build command line, so we can parse our way - make sure there is trailing space */
        String cmdline = "";
        for(int i = 1; i < args.length; i++) {
            cmdline += args[i] + " ";
        }
        boolean inquote = false;
        StringBuilder sb = new StringBuilder();
        String varid = null;
        for(int i = 0; i < cmdline.length(); i++) {
            char c = cmdline.charAt(i);
            if(inquote) {   /* If in quote, accumulate until end or another quote */
                if(c == '\"') { /* End quote */
                    inquote = false;
                    if(varid == null) { /* No varid? */
                        rslt.put(ARG_LABEL, sb.toString());
                    }
                    else {
                        rslt.put(varid, sb.toString());
                        varid = null;
                    }
                    sb.setLength(0);
                }
                else {
                    sb.append(c);
                }
            }
            else if(c == '\"') {    /* Start of quote? */
                inquote = true;
            }
            else if(c == ':') { /* var:value */
                varid = sb.toString();  /* Save variable ID */
                sb.setLength(0);
            }
            else if(c == ' ') { /* Ending space? */
                if(varid == null) { /* No varid? */
                    if(sb.length() > 0) {
                        rslt.put(ARG_LABEL, sb.toString());
                    }
                }
                else {
                    rslt.put(varid, sb.toString());
                    varid = null;
                }
                sb.setLength(0);
            }
            else {
                sb.append(c);
            }
        }
        if(inquote) {   /* If still in quote, syntax error */
            snd.sendMessage("Error: unclosed doublequote");
            return null;
        }
        return rslt;
    }
    
    public static boolean onCommand(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, setid, file, label, newlabel, iconid, prio, minzoom;
        String x, y, z, world, normalized_world;
        String deficon, newset;
        
        if(api == null) {
            sender.sendMessage("Markers component is not enabled.");
            return false;
        }
        if(args.length == 0)
            return false;
        DynmapPlayer player = null;
        if (sender instanceof DynmapPlayer)
            player = (DynmapPlayer) sender;
        /* Check if valid command */
        String c = args[0];
        if (!commands.contains(c)) {
            return false;
        }
        /* Process commands */
        if(c.equals("add") && api.core.checkPlayerPermission(sender, "marker.add")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                iconid = parms.get(ARG_ICON);
                setid = parms.get(ARG_SET);
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                x = parms.get(ARG_X);
                y = parms.get(ARG_Y);
                z = parms.get(ARG_Z);
                world = DynmapWorld.normalizeWorldName(parms.get(ARG_WORLD));
                if(world != null) {
                    normalized_world = DynmapWorld.normalizeWorldName(world);
                    if(api.core.getWorld(normalized_world) == null) {
                        sender.sendMessage("Invalid world ID: " + world);
                        return true;
                    }
                }
                DynmapLocation loc = null;
                if((x == null) && (y == null) && (z == null) && (world == null)) {
                    if(player == null) {
                        sender.sendMessage("Must be issued by player, or x, y, z, and world parameters are required");
                        return true;
                    }
                    loc = player.getLocation();
                }
                else if((x != null) && (y != null) && (z != null) && (world != null)) {
                    try {
                        loc = new DynmapLocation(world, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
                    } catch (NumberFormatException nfx) {
                        sender.sendMessage("Coordinates x, y, and z must be numbers");
                        return true;
                    }
                }
                else {
                    sender.sendMessage("Must be issued by player, or x, y, z, and world parameters are required");
                    return true;
                }
                /* Fill in defaults for missing parameters */
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                /* Add new marker */
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid set - " + setid);
                    return true;
                }
                MarkerIcon ico = null;
                if(iconid == null) {
                    ico = set.getDefaultMarkerIcon();
                }
                if(ico == null) {
                    if(iconid == null) {
                        iconid = MarkerIcon.DEFAULT;
                    }
                    ico = api.getMarkerIcon(iconid);
                }
                if(ico == null) {
                    sender.sendMessage("Error: invalid icon - " + iconid);
                    return true;
                }
                Marker m = set.createMarker(id, label, 
                        loc.world, loc.x, loc.y, loc.z, ico, true);
                if(m == null) {
                    sender.sendMessage("Error creating marker");
                }
                else {
                    sender.sendMessage("Added marker id:'" + m.getMarkerID() + "' (" + m.getLabel() + ") to set '" + set.getMarkerSetID() + "'");
                }
            }
            else {
                sender.sendMessage("Marker label required");
            }
        }
        /* Update position of bookmark - must have ID parameter */
        else if(c.equals("movehere") && plugin.checkPlayerPermission(sender, "marker.movehere")) {
            if(player == null) {
                sender.sendMessage("Command can only be used by player");
            }
            else if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                setid = parms.get(ARG_SET);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<marker-id> required");
                    return true;
                }
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid set - " + setid);
                    return true;
                }
                Marker marker;
                if(id != null) {
                    marker = set.findMarker(id);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: marker not found - " + id);
                        return true;
                    }
                }
                else {
                    marker = set.findMarkerByLabel(label);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: marker not found - " + label);
                        return true;
                    }
                }
                DynmapLocation loc = player.getLocation();
                marker.setLocation(loc.world, loc.x, loc.y, loc.z);
                sender.sendMessage("Updated location of marker id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<marker-id> required");
            }
        }
        /* Update other attributes of marker - must have ID parameter */
        else if(c.equals("update") && plugin.checkPlayerPermission(sender, "marker.update")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                setid = parms.get(ARG_SET);
                newset = parms.get(ARG_NEWSET);
                x = parms.get(ARG_X);
                y = parms.get(ARG_Y);
                z = parms.get(ARG_Z);
                world = parms.get(ARG_WORLD);
                if(world != null) {
                    if(api.core.getWorld(world) == null) {
                        sender.sendMessage("Invalid world ID: " + world);
                        return true;
                    }
                }
                DynmapLocation loc = null;
                if((x != null) && (y != null) && (z != null) && (world != null)) {
                    try {
                        loc = new DynmapLocation(world, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
                    } catch (NumberFormatException nfx) {
                        sender.sendMessage("Coordinates x, y, and z must be numbers");
                        return true;
                    }
                }

                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<marker-id> required");
                    return true;
                }
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid set - " + setid);
                    return true;
                }
                Marker marker;
                if(id != null) {
                    marker = set.findMarker(id);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: marker not found - " + id);
                        return true;
                    }
                }
                else {
                    marker = set.findMarkerByLabel(label);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: marker not found - " + label);
                        return true;
                    }
                }
                newlabel = parms.get(ARG_NEWLABEL);
                if(newlabel != null) {    /* Label set? */
                    marker.setLabel(newlabel);
                }
                iconid = parms.get(ARG_ICON);
                if(iconid != null) {
                    MarkerIcon ico = api.getMarkerIcon(iconid);
                    if(ico == null) {
                        sender.sendMessage("Error: invalid icon - " + iconid);
                        return true;
                    }
                    marker.setMarkerIcon(ico);
                }
                if(loc != null)
                    marker.setLocation(loc.world, loc.x, loc.y, loc.z);
                if(newset != null) {
                    MarkerSet ms = api.getMarkerSet(newset);
                    if(ms == null) {
                        sender.sendMessage("Error: invalid new marker set - " + newset);
                        return true;
                    }
                    marker.setMarkerSet(ms);
                }
                sender.sendMessage("Updated marker id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<marker-id> required");
            }
        }
        /* Delete marker - must have ID parameter */
        else if(c.equals("delete") && plugin.checkPlayerPermission(sender, "marker.delete")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                setid = parms.get(ARG_SET);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<marker-id> required");
                    return true;
                }
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid set - " + setid);
                    return true;
                }
                Marker marker;
                if(id != null) {
                    marker = set.findMarker(id);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: marker not found - " + id);
                        return true;
                    }
                }
                else {
                    marker = set.findMarkerByLabel(label);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: marker not found - " + label);
                        return true;
                    }
                }
                marker.deleteMarker();
                sender.sendMessage("Deleted marker id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<marker-id> required");
            }
        }
        /* List markers */
        else if(c.equals("list") && plugin.checkPlayerPermission(sender, "marker.list")) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            setid = parms.get(ARG_SET);
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            Set<Marker> markers = set.getMarkers();
            TreeMap<String, Marker> sortmarkers = new TreeMap<String, Marker>();
            for(Marker m : markers) {
                sortmarkers.put(m.getMarkerID(), m);
            }
            for(String s : sortmarkers.keySet()) {
                Marker m = sortmarkers.get(s);
                sender.sendMessage(m.getMarkerID() + ": label:\"" + m.getLabel() + "\", set:" + m.getMarkerSet().getMarkerSetID() + 
                                   ", world:" + m.getWorld() + ", x:" + m.getX() + ", y:" + m.getY() + ", z:" + m.getZ() + 
                                   ", icon:" + m.getMarkerIcon().getMarkerIconID());
            }
        }
        /* List icons */
        else if(c.equals("icons") && plugin.checkPlayerPermission(sender, "marker.icons")) {
            Set<String> iconids = new TreeSet<String>(api.markericons.keySet());
            for(String s : iconids) {
                MarkerIcon ico = api.markericons.get(s);
                sender.sendMessage(ico.getMarkerIconID() + ": label:\"" + ico.getMarkerIconLabel() + "\", builtin:" + ico.isBuiltIn());
            }
        }
        else if(c.equals("addset") && plugin.checkPlayerPermission(sender, "marker.addset")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                prio = parms.get(ARG_PRIO);
                minzoom = parms.get(ARG_MINZOOM);
                deficon = parms.get(ARG_DEFICON);
                if(deficon == null) {
                    deficon = MarkerIcon.DEFAULT;
                }
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<marker-id> required");
                    return true;
                }
                if(label == null)
                    label = id;
                if(id == null)
                    id = label;
                /* See if marker set exists */
                MarkerSet set = api.getMarkerSet(id);
                if(set != null) {
                    sender.sendMessage("Error: set already exists - id:" + set.getMarkerSetID());
                    return true;
                }
                /* Create new set */
                set = api.createMarkerSet(id, label, null, true);
                if(set == null) {
                    sender.sendMessage("Error creating set");
                }
                else {
                    String h = parms.get(ARG_HIDE);
                    if((h != null) && (h.equals("true")))
                        set.setHideByDefault(true);
                    String showlabels = parms.get(ARG_SHOWLABEL);
                    if(showlabels != null) {
                        if(showlabels.equals("true"))
                            set.setLabelShow(true);
                        else if(showlabels.equals("false"))
                            set.setLabelShow(false);
                    }
                    if(prio != null) {
                        try {
                            set.setLayerPriority(Integer.valueOf(prio));
                        } catch (NumberFormatException nfx) {
                            sender.sendMessage("Invalid priority: " + prio);
                        }
                    }
                    MarkerIcon mi = MarkerAPIImpl.getMarkerIconImpl(deficon);
                    if(mi != null) {
                        set.setDefaultMarkerIcon(mi);
                    }
                    else {
                        sender.sendMessage("Invalid default icon: " + deficon);
                    }
                    if(minzoom != null) {
                        try {
                            set.setMinZoom(Integer.valueOf(minzoom));
                        } catch (NumberFormatException nfx) {
                            sender.sendMessage("Invalid max zoom out: " + minzoom);
                        }
                    }
                    sender.sendMessage("Added set id:'" + set.getMarkerSetID() + "' (" + set.getMarkerSetLabel() + ")");
                }
            }
            else {
                sender.sendMessage("<label> or id:<set-id> required");
            }
        }
        else if(c.equals("updateset") && plugin.checkPlayerPermission(sender, "marker.updateset")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                prio = parms.get(ARG_PRIO);
                minzoom = parms.get(ARG_MINZOOM);
                deficon = parms.get(ARG_DEFICON);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<set-id> required");
                    return true;
                }
                MarkerSet set = null;
                if(id != null) {
                    set = api.getMarkerSet(id);
                    if(set == null) {
                        sender.sendMessage("Error: set does not exist - id:" + id);
                        return true;
                    }
                }
                else {
                    Set<MarkerSet> sets = api.getMarkerSets();
                    for(MarkerSet s : sets) {
                        if(s.getMarkerSetLabel().equals(label)) {
                            set = s;
                            break;
                        }
                    }
                    if(set == null) {
                        sender.sendMessage("Error: matching set not found");
                        return true;                        
                    }
                }
                newlabel = parms.get(ARG_NEWLABEL);
                if(newlabel != null) {
                    set.setMarkerSetLabel(newlabel);
                }
                String hide = parms.get(ARG_HIDE);
                if(hide != null) {
                    set.setHideByDefault(hide.equals("true"));
                }
                String showlabels = parms.get(ARG_SHOWLABEL);
                if(showlabels != null) {
                    if(showlabels.equals("true"))
                        set.setLabelShow(true);
                    else if(showlabels.equals("false"))
                        set.setLabelShow(false);
                    else
                        set.setLabelShow(null);
                }
                if(deficon != null) {
                    MarkerIcon mi = null;
                    if(deficon.equals("") == false) {
                        mi = MarkerAPIImpl.getMarkerIconImpl(deficon);
                        if(mi == null) {
                            sender.sendMessage("Error: invalid marker icon - " + deficon);
                        }
                    }
                    set.setDefaultMarkerIcon(mi);
                }

                if(prio != null) {
                    try {
                        set.setLayerPriority(Integer.valueOf(prio));
                    } catch (NumberFormatException nfx) {
                        sender.sendMessage("Invalid priority: " + prio);
                    }
                }
                if(minzoom != null) {
                    try {
                        set.setMinZoom(Integer.valueOf(minzoom));
                    } catch (NumberFormatException nfx) {
                        sender.sendMessage("Invalid min zoom: " + minzoom);
                    }
                }
                sender.sendMessage("Set '" + set.getMarkerSetID() + "' updated");
            }
            else {
                sender.sendMessage("<label> or id:<set-id> required");
            }
        }
        else if(c.equals("deleteset") && plugin.checkPlayerPermission(sender, "marker.deleteset")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<set-id> required");
                    return true;
                }
                if(id != null) {
                    MarkerSet set = api.getMarkerSet(id);
                    if(set == null) {
                        sender.sendMessage("Error: set does not exist - id:" + id);
                        return true;
                    }
                    set.deleteMarkerSet();
                }
                else {
                    Set<MarkerSet> sets = api.getMarkerSets();
                    MarkerSet set = null;
                    for(MarkerSet s : sets) {
                        if(s.getMarkerSetLabel().equals(label)) {
                            set = s;
                            break;
                        }
                    }
                    if(set == null) {
                        sender.sendMessage("Error: matching set not found");
                        return true;                        
                    }
                    set.deleteMarkerSet();
                }
                sender.sendMessage("Deleted set");
            }
            else {
                sender.sendMessage("<label> or id:<set-id> required");
            }
        }
        /* List sets */
        else if(c.equals("listsets") && plugin.checkPlayerPermission(sender, "marker.listsets")) {
            Set<String> setids = new TreeSet<String>(api.markersets.keySet());
            for(String s : setids) {
                MarkerSet set = api.markersets.get(s);
                Boolean b = set.getLabelShow();
                MarkerIcon defi = set.getDefaultMarkerIcon();
                sender.sendMessage(set.getMarkerSetID() + ": label:\"" + set.getMarkerSetLabel() + "\", hide:" + set.getHideByDefault() + ", prio:" + set.getLayerPriority() + ", minzoom:" + set.getMinZoom() + 
                        ", showlabels:" + ((b != null)?b:"null") + ", deficon:" + ((defi != null)?defi.getMarkerIconID():""));
            }
        }
        /* Add new icon */
        else if(c.equals("addicon") && plugin.checkPlayerPermission(sender, "marker.addicon")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                file = parms.get(ARG_FILE);
                label = parms.get(ARG_LABEL);
                if(id == null) {
                    sender.sendMessage("id:<icon-id> required");
                    return true;
                }
                if(file == null) {
                    sender.sendMessage("file:\"filename\" required");
                    return true;
                }
                if(label == null)
                    label = id;
                MarkerIcon ico = MarkerAPIImpl.getMarkerIconImpl(id);
                if(ico != null) {
                    sender.sendMessage("Icon '" + id + "' already defined.");
                    return true;
                }
                /* Open stream to filename */
                File iconf = new File(file);
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(iconf);
                    /* Create new icon */
                    MarkerIcon mi = api.createMarkerIcon(id, label, fis);
                    if(mi == null) {
                        sender.sendMessage("Error creating icon");
                        return true;
                    }
                } catch (IOException iox) {
                    sender.sendMessage("Error loading icon file - " + iox);
                } finally {
                    if(fis != null) {
                        try { fis.close(); } catch (IOException iox) {}
                    }
                }
            }
            else {
                sender.sendMessage("id:<icon-id> and file:\"filename\" required");
            }
        }
        else if(c.equals("updateicon") && plugin.checkPlayerPermission(sender, "marker.updateicon")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                newlabel = parms.get(ARG_NEWLABEL);
                file = parms.get(ARG_FILE);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<icon-id> required");
                    return true;
                }
                MarkerIcon ico = null;
                if(id != null) {
                    ico = MarkerAPIImpl.getMarkerIconImpl(id);
                    if(ico == null) {
                        sender.sendMessage("Error: icon does not exist - id:" + id);
                        return true;
                    }
                }
                else {
                    Set<MarkerIcon> icons = api.getMarkerIcons();
                    for(MarkerIcon ic : icons) {
                        if(ic.getMarkerIconLabel().equals(label)) {
                            ico = ic;
                            break;
                        }
                    }
                    if(ico == null) {
                        sender.sendMessage("Error: matching icon not found");
                        return true;                        
                    }
                }
                if(newlabel != null) {
                    ico.setMarkerIconLabel(newlabel);
                }
                /* Handle new file */
                if(file != null) {
                    File iconf = new File(file);
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(iconf);
                        ico.setMarkerIconImage(fis);                        
                    } catch (IOException iox) {
                        sender.sendMessage("Error loading icon file - " + iox);
                    } finally {
                        if(fis != null) {
                            try { fis.close(); } catch (IOException iox) {}
                        }
                    }
                }
                sender.sendMessage("Icon '" + ico.getMarkerIconID() + "' updated");
            }
            else {
                sender.sendMessage("<label> or id:<icon-id> required");
            }
        }
        else if(c.equals("deleteicon") && plugin.checkPlayerPermission(sender, "marker.deleteicon")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<icon-id> required");
                    return true;
                }
                if(id != null) {
                    MarkerIcon ico = MarkerAPIImpl.getMarkerIconImpl(id);
                    if(ico == null) {
                        sender.sendMessage("Error: icon does not exist - id:" + id);
                        return true;
                    }
                    ico.deleteIcon();
                }
                else {
                    Set<MarkerIcon> icos = api.getMarkerIcons();
                    MarkerIcon ico = null;
                    for(MarkerIcon ic : icos) {
                        if(ic.getMarkerIconLabel().equals(label)) {
                            ico = ic;
                            break;
                        }
                    }
                    if(ico == null) {
                        sender.sendMessage("Error: matching icon not found");
                        return true;                        
                    }
                    ico.deleteIcon();
                }
                sender.sendMessage("Deleted marker icon");
            }
            else {
                sender.sendMessage("<label> or id:<icon-id> required");
            }
        }
        /* Add point to accumulator */
        else if(c.equals("addcorner") && plugin.checkPlayerPermission(sender, "marker.addarea")) {
            DynmapLocation loc = null;
            if(player == null) {
                id = "-console-";
            }
            else {
                id = player.getName();
                loc = player.getLocation();
            }
            List<DynmapLocation> ll = api.pointaccum.get(id); /* Find list */
            
            if(args.length > 3) {   /* Enough for coord */
                String w = null;
                if(args.length == 4) {  /* No world */
                    if(ll == null) {    /* No points?  Error */
                        sender.sendMessage("First added corner needs world ID after coordinates");
                        return true;
                    }
                    else {
                        w = ll.get(0).world;   /* Use same world */
                    }
                }
                else {  /* Get world ID */
                    w = args[4];
                    if(api.core.getWorld(w) == null) {
                        sender.sendMessage("Invalid world ID: " + args[3]);
                        return true;
                    }
                }
                try {
                    loc = new DynmapLocation(w, Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]));
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Bad format: /dmarker addcorner <x> <y> <z> <world>");
                    return true;
                }
            }
            if(loc == null) {
                sender.sendMessage("Console must supply corner coordinates: <x> <y> <z> <world>");
                return true;
            }
            if(ll == null) {
                ll = new ArrayList<DynmapLocation>();
                api.pointaccum.put(id, ll);
            }
            else {  /* Else, if list exists, see if world matches */
                if(ll.get(0).world.equals(loc.world) == false) {
                    ll.clear(); /* Reset list - point on new world */
                }
            }
            ll.add(loc);
            sender.sendMessage("Added corner #" + ll.size() + " at {" + loc.x + "," + loc.y + "," + loc.z + "} to list");
        }
        else if(c.equals("clearcorners") && plugin.checkPlayerPermission(sender, "marker.addarea")) {
            if(player == null) {
                id = "-console-";
            }
            else {
                id = player.getName();
            }
            api.pointaccum.remove(id);
            sender.sendMessage("Cleared corner list");
        }
        else if(c.equals("addarea") && plugin.checkPlayerPermission(sender, "marker.addarea")) {
            String pid;
            if(player == null) {
                pid = "-console-";
            }
            else {
                pid = player.getName();
            }
            List<DynmapLocation> ll = api.pointaccum.get(pid); /* Find list */
            if((ll == null) || (ll.size() < 2)) {   /* Not enough points? */
                sender.sendMessage("At least two corners must be added with /dmarker addcorner before an area can be added");
                return true;
            }
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            setid = parms.get(ARG_SET);
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            /* Fill in defaults for missing parameters */
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            /* Add new marker */
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            /* Make coord list */
            double[] xx = new double[ll.size()];
            double[] zz = new double[ll.size()];
            for(int i = 0; i < ll.size(); i++) {
                DynmapLocation loc = ll.get(i);
                xx[i] = loc.x;
                zz[i] = loc.z;
            }
            /* Make area marker */
            AreaMarker m = set.createAreaMarker(id, label, false, ll.get(0).world, xx, zz, true);
            if(m == null) {
                sender.sendMessage("Error creating area");
            }
            else {
                /* Process additional attributes, if any */
                processAreaArgs(sender, m, parms);
                
                sender.sendMessage("Added area id:'" + m.getMarkerID() + "' (" + m.getLabel() + ") to set '" + set.getMarkerSetID() + "'");
                api.pointaccum.remove(pid); /* Clear corner list */
            }
        }
        /* List areas */
        else if(c.equals("listareas") && plugin.checkPlayerPermission(sender, "marker.listareas")) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            setid = parms.get(ARG_SET);
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            Set<AreaMarker> markers = set.getAreaMarkers();
            TreeMap<String, AreaMarker> sortmarkers = new TreeMap<String, AreaMarker>();
            for(AreaMarker m : markers) {
                sortmarkers.put(m.getMarkerID(), m);
            }
            for(String s : sortmarkers.keySet()) {
                AreaMarker m = sortmarkers.get(s);
                String ptlist = "{ ";
                for(int i = 0; i < m.getCornerCount(); i++) {
                    ptlist += "{" + m.getCornerX(i) + "," + m.getCornerZ(i)+ "} ";
                }
                ptlist += "}";
                sender.sendMessage(m.getMarkerID() + ": label:\"" + m.getLabel() + "\", set:" + m.getMarkerSet().getMarkerSetID() + 
                                   ", world:" + m.getWorld() + ", corners:" + ptlist + 
                                   ", weight: " + m.getLineWeight() + ", color:" + String.format("%06x", m.getLineColor()) +
                                   ", opacity: " + m.getLineOpacity() + ", fillcolor: " + String.format("%06x", m.getFillColor()) +
                                   ", fillopacity: " + m.getFillOpacity());
            }
        }
        /* Delete area - must have ID parameter */
        else if(c.equals("deletearea") && plugin.checkPlayerPermission(sender, "marker.deletearea")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                setid = parms.get(ARG_SET);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<area-id> required");
                    return true;
                }
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid set - " + setid);
                    return true;
                }
                AreaMarker marker;
                if(id != null) {
                    marker = set.findAreaMarker(id);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: area not found - " + id);
                        return true;
                    }
                }
                else {
                    marker = set.findAreaMarkerByLabel(label);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: area not found - " + label);
                        return true;
                    }
                }
                marker.deleteMarker();
                sender.sendMessage("Deleted area id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<area-id> required");
            }
        }
        /* Update other attributes of area - must have ID parameter */
        else if(c.equals("updatearea") && plugin.checkPlayerPermission(sender, "marker.updatearea")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                setid = parms.get(ARG_SET);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<area-id> required");
                    return true;
                }
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid set - " + setid);
                    return true;
                }
                AreaMarker marker;
                if(id != null) {
                    marker = set.findAreaMarker(id);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: area not found - " + id);
                        return true;
                    }
                }
                else {
                    marker = set.findAreaMarkerByLabel(label);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: area not found - " + label);
                        return true;
                    }
                }
                newlabel = parms.get(ARG_NEWLABEL);
                if(newlabel != null) {    /* Label set? */
                    marker.setLabel(newlabel);
                }
                if(!processAreaArgs(sender,marker, parms))
                    return true;
                sender.sendMessage("Updated area id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<area-id> required");
            }
        }
        
        else if(c.equals("addline") && plugin.checkPlayerPermission(sender, "marker.addline")) {
            String pid;
            if(player == null) {
                pid = "-console-";
            }
            else {
                pid = player.getName();
            }
            List<DynmapLocation> ll = api.pointaccum.get(pid); /* Find list */
            if((ll == null) || (ll.size() < 2)) {   /* Not enough points? */
                sender.sendMessage("At least two corners must be added with /dmarker addcorner before a line can be added");
                return true;
            }
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            setid = parms.get(ARG_SET);
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            /* Fill in defaults for missing parameters */
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            /* Add new marker */
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            /* Make coord list */
            double[] xx = new double[ll.size()];
            double[] yy = new double[ll.size()];
            double[] zz = new double[ll.size()];
            for(int i = 0; i < ll.size(); i++) {
                DynmapLocation loc = ll.get(i);
                xx[i] = loc.x;
                yy[i] = loc.y;
                zz[i] = loc.z;
            }
            /* Make poly-line marker */
            PolyLineMarker m = set.createPolyLineMarker(id, label, false, ll.get(0).world, xx, yy, zz, true);
            if(m == null) {
                sender.sendMessage("Error creating line");
            }
            else {
                /* Process additional attributes, if any */
                processPolyArgs(sender, m, parms);
                
                sender.sendMessage("Added line id:'" + m.getMarkerID() + "' (" + m.getLabel() + ") to set '" + set.getMarkerSetID() + "'");
                api.pointaccum.remove(pid); /* Clear corner list */
            }
        }
        /* List poly-lines */
        else if(c.equals("listlines") && plugin.checkPlayerPermission(sender, "marker.listlines")) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            setid = parms.get(ARG_SET);
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            Set<PolyLineMarker> markers = set.getPolyLineMarkers();
            TreeMap<String, PolyLineMarker> sortmarkers = new TreeMap<String, PolyLineMarker>();
            for(PolyLineMarker m : markers) {
                sortmarkers.put(m.getMarkerID(), m);
            }
            for(String s : sortmarkers.keySet()) {
                PolyLineMarker m = sortmarkers.get(s);
                String ptlist = "{ ";
                for(int i = 0; i < m.getCornerCount(); i++) {
                    ptlist += "{" + m.getCornerX(i) + "," + m.getCornerY(i) + "," + m.getCornerZ(i) + "} ";
                }
                ptlist += "}";
                sender.sendMessage(m.getMarkerID() + ": label:\"" + m.getLabel() + "\", set:" + m.getMarkerSet().getMarkerSetID() + 
                                   ", world:" + m.getWorld() + ", corners:" + ptlist + 
                                   ", weight: " + m.getLineWeight() + ", color:" + String.format("%06x", m.getLineColor()) +
                                   ", opacity: " + m.getLineOpacity());
            }
        }
        /* Delete poly-line - must have ID parameter */
        else if(c.equals("deleteline") && plugin.checkPlayerPermission(sender, "marker.deleteline")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                setid = parms.get(ARG_SET);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<line-id> required");
                    return true;
                }
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid set - " + setid);
                    return true;
                }
                PolyLineMarker marker;
                if(id != null) {
                    marker = set.findPolyLineMarker(id);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: line not found - " + id);
                        return true;
                    }
                }
                else {
                    marker = set.findPolyLineMarkerByLabel(label);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: line not found - " + label);
                        return true;
                    }
                }
                marker.deleteMarker();
                sender.sendMessage("Deleted poly-line id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<line-id> required");
            }
        }
        /* Update other attributes of poly-line - must have ID parameter */
        else if(c.equals("updateline") && plugin.checkPlayerPermission(sender, "marker.updateline")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                setid = parms.get(ARG_SET);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<line-id> required");
                    return true;
                }
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid set - " + setid);
                    return true;
                }
                PolyLineMarker marker;
                if(id != null) {
                    marker = set.findPolyLineMarker(id);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: line not found - " + id);
                        return true;
                    }
                }
                else {
                    marker = set.findPolyLineMarkerByLabel(label);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: line not found - " + label);
                        return true;
                    }
                }
                newlabel = parms.get(ARG_NEWLABEL);
                if(newlabel != null) {    /* Label set? */
                    marker.setLabel(newlabel);
                }
                if(!processPolyArgs(sender,marker, parms))
                    return true;
                sender.sendMessage("Updated line id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<line-id> required");
            }
        }

        else if(c.equals("addcircle") && plugin.checkPlayerPermission(sender, "marker.addcircle")) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            setid = parms.get(ARG_SET);
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            x = parms.get(ARG_X);
            y = parms.get(ARG_Y);
            z = parms.get(ARG_Z);
            world = parms.get(ARG_WORLD);
            if(world != null) {
                if(api.core.getWorld(world) == null) {
                    sender.sendMessage("Invalid world ID: " + world);
                    return true;
                }
            }
            DynmapLocation loc = null;
            if((x == null) && (y == null) && (z == null) && (world == null)) {
                if(player == null) {
                    sender.sendMessage("Must be issued by player, or x, y, z, and world parameters are required");
                    return true;
                }
                loc = player.getLocation();
            }
            else if((x != null) && (y != null) && (z != null) && (world != null)) {
                try {
                    loc = new DynmapLocation(world, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Coordinates x, y, and z must be numbers");
                    return true;
                }
            }
            else {
                sender.sendMessage("Must be issued by player, or x, y, z, and world parameters are required");
                return true;
            }
            /* Fill in defaults for missing parameters */
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            /* Add new marker */
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            
            /* Make circle marker */
            CircleMarker m = set.createCircleMarker(id, label, false, loc.world, loc.x, loc.y, loc.z, 1, 1, true);
            if(m == null) {
                sender.sendMessage("Error creating circle");
            }
            else {
                /* Process additional attributes, if any */
                processCircleArgs(sender, m, parms);
                
                sender.sendMessage("Added circle id:'" + m.getMarkerID() + "' (" + m.getLabel() + ") to set '" + set.getMarkerSetID() + "'");
            }
        }
        /* List circles */
        else if(c.equals("listcircles") && plugin.checkPlayerPermission(sender, "marker.listcircles")) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            setid = parms.get(ARG_SET);
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            Set<CircleMarker> markers = set.getCircleMarkers();
            TreeMap<String, CircleMarker> sortmarkers = new TreeMap<String, CircleMarker>();
            for(CircleMarker m : markers) {
                sortmarkers.put(m.getMarkerID(), m);
            }
            for(String s : sortmarkers.keySet()) {
                CircleMarker m = sortmarkers.get(s);
                sender.sendMessage(m.getMarkerID() + ": label:\"" + m.getLabel() + "\", set:" + m.getMarkerSet().getMarkerSetID() + 
                                   ", world:" + m.getWorld() + ", center:" + m.getCenterX() + "/" + m.getCenterY() + "/" + m.getCenterZ() +
                                   ", radius:" + m.getRadiusX() + "/" + m.getRadiusZ() +
                                   ", weight: " + m.getLineWeight() + ", color:" + String.format("%06x", m.getLineColor()) +
                                   ", opacity: " + m.getLineOpacity() + ", fillcolor: " + String.format("%06x", m.getFillColor()) +
                                   ", fillopacity: " + m.getFillOpacity());
            }
        }
        /* Delete circle - must have ID parameter */
        else if(c.equals("deletecircle") && plugin.checkPlayerPermission(sender, "marker.deletecircle")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                setid = parms.get(ARG_SET);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<circle-id> required");
                    return true;
                }
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid set - " + setid);
                    return true;
                }
                CircleMarker marker;
                if(id != null) {
                    marker = set.findCircleMarker(id);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: circle not found - " + id);
                        return true;
                    }
                }
                else {
                    marker = set.findCircleMarkerByLabel(label);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: circle not found - " + label);
                        return true;
                    }
                }
                marker.deleteMarker();
                sender.sendMessage("Deleted circle id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<circle-id> required");
            }
        }
        /* Update other attributes of circle - must have ID parameter */
        else if(c.equals("updatecircle") && plugin.checkPlayerPermission(sender, "marker.updatecircle")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                setid = parms.get(ARG_SET);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<area-id> required");
                    return true;
                }
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid set - " + setid);
                    return true;
                }
                CircleMarker marker;
                if(id != null) {
                    marker = set.findCircleMarker(id);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: circle not found - " + id);
                        return true;
                    }
                }
                else {
                    marker = set.findCircleMarkerByLabel(label);
                    if(marker == null) {    /* No marker */
                        sender.sendMessage("Error: circle not found - " + label);
                        return true;
                    }
                }
                newlabel = parms.get(ARG_NEWLABEL);
                if(newlabel != null) {    /* Label set? */
                    marker.setLabel(newlabel);
                }
                if(!processCircleArgs(sender,marker, parms))
                    return true;
                sender.sendMessage("Updated circle id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<circle-id> required");
            }
        }

        else {
            return false;
        }
        return true;
    }

    /**
     * Write markers file for given world
     */
    private void writeMarkersFile(String wname) {
        Map<String, Object> markerdata = new HashMap<String, Object>();

        File f = new File(markertiledir, "marker_" + wname + ".json");
        File fnew = new File(markertiledir, "marker_" + wname + ".json.new");
                
        Map<String, Object> worlddata = new HashMap<String, Object>();
        worlddata.put("timestamp", Long.valueOf(System.currentTimeMillis()));   /* Add timestamp */

        for(MarkerSet ms : markersets.values()) {
            HashMap<String, Object> msdata = new HashMap<String, Object>();
            msdata.put("label", ms.getMarkerSetLabel());
            msdata.put("hide", ms.getHideByDefault());
            msdata.put("layerprio", ms.getLayerPriority());
            msdata.put("minzoom", ms.getMinZoom());
            if(ms.getLabelShow() != null) {
                msdata.put("showlabels", ms.getLabelShow());
            }
            HashMap<String, Object> markers = new HashMap<String, Object>();
            for(Marker m : ms.getMarkers()) {
                if(m.getWorld().equals(wname) == false) continue;
                
                HashMap<String, Object> mdata = new HashMap<String, Object>();
                mdata.put("x", m.getX());
                mdata.put("y", m.getY());
                mdata.put("z", m.getZ());
                MarkerIcon mi = m.getMarkerIcon();
                if(mi == null)
                    mi = MarkerAPIImpl.getMarkerIconImpl(MarkerIcon.DEFAULT);
                mdata.put("icon", mi.getMarkerIconID());
                mdata.put("dim", mi.getMarkerIconSize().getSize());
                mdata.put("label", m.getLabel());
                mdata.put("markup", m.isLabelMarkup());
                if(m.getDescription() != null)
                    mdata.put("desc", m.getDescription());
                /* Add to markers */
                markers.put(m.getMarkerID(), mdata);
            }
            msdata.put("markers", markers); /* Add markers to set data */

            HashMap<String, Object> areas = new HashMap<String, Object>();
            for(AreaMarker m : ms.getAreaMarkers()) {
                if(m.getWorld().equals(wname) == false) continue;
                
                HashMap<String, Object> mdata = new HashMap<String, Object>();
                int cnt = m.getCornerCount();
                List<Double> xx = new ArrayList<Double>();
                List<Double> zz = new ArrayList<Double>();
                for(int i = 0; i < cnt; i++) {
                    xx.add(m.getCornerX(i));
                    zz.add(m.getCornerZ(i));
                }
                mdata.put("x", xx);
                mdata.put("ytop", m.getTopY());
                mdata.put("ybottom", m.getBottomY());
                mdata.put("z", zz);
                mdata.put("color", String.format("#%06X", m.getLineColor()));
                mdata.put("fillcolor", String.format("#%06X", m.getFillColor()));
                mdata.put("opacity", m.getLineOpacity());
                mdata.put("fillopacity", m.getFillOpacity());
                mdata.put("weight", m.getLineWeight());
                mdata.put("label", m.getLabel());
                mdata.put("markup", m.isLabelMarkup());
                if(m.getDescription() != null)
                    mdata.put("desc", m.getDescription());
                /* Add to markers */
                areas.put(m.getMarkerID(), mdata);
            }
            msdata.put("areas", areas); /* Add areamarkers to set data */

            HashMap<String, Object> lines = new HashMap<String, Object>();
            for(PolyLineMarker m : ms.getPolyLineMarkers()) {
                if(m.getWorld().equals(wname) == false) continue;
                
                HashMap<String, Object> mdata = new HashMap<String, Object>();
                int cnt = m.getCornerCount();
                List<Double> xx = new ArrayList<Double>();
                List<Double> yy = new ArrayList<Double>();
                List<Double> zz = new ArrayList<Double>();
                for(int i = 0; i < cnt; i++) {
                    xx.add(m.getCornerX(i));
                    yy.add(m.getCornerY(i));
                    zz.add(m.getCornerZ(i));
                }
                mdata.put("x", xx);
                mdata.put("y", yy);
                mdata.put("z", zz);
                mdata.put("color", String.format("#%06X", m.getLineColor()));
                mdata.put("opacity", m.getLineOpacity());
                mdata.put("weight", m.getLineWeight());
                mdata.put("label", m.getLabel());
                mdata.put("markup", m.isLabelMarkup());
                if(m.getDescription() != null)
                    mdata.put("desc", m.getDescription());
                /* Add to markers */
                lines.put(m.getMarkerID(), mdata);
            }
            msdata.put("lines", lines); /* Add polylinemarkers to set data */

            HashMap<String, Object> circles = new HashMap<String, Object>();
            for(CircleMarker m : ms.getCircleMarkers()) {
                if(m.getWorld().equals(wname) == false) continue;
                
                HashMap<String, Object> mdata = new HashMap<String, Object>();
                mdata.put("x", m.getCenterX());
                mdata.put("y", m.getCenterY());
                mdata.put("z", m.getCenterZ());
                mdata.put("xr", m.getRadiusX());
                mdata.put("zr", m.getRadiusZ());
                mdata.put("color", String.format("#%06X", m.getLineColor()));
                mdata.put("fillcolor", String.format("#%06X", m.getFillColor()));
                mdata.put("opacity", m.getLineOpacity());
                mdata.put("fillopacity", m.getFillOpacity());
                mdata.put("weight", m.getLineWeight());
                mdata.put("label", m.getLabel());
                mdata.put("markup", m.isLabelMarkup());
                if(m.getDescription() != null)
                    mdata.put("desc", m.getDescription());
                /* Add to markers */
                circles.put(m.getMarkerID(), mdata);
            }
            msdata.put("circles", circles); /* Add circle markers to set data */

            markerdata.put(ms.getMarkerSetID(), msdata);    /* Add marker set data to world marker data */
        }
        worlddata.put("sets", markerdata);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(fnew);
            fos.write(Json.stringifyJson(worlddata).getBytes());
        } catch (FileNotFoundException ex) {
            Log.severe("Exception while writing JSON-file.", ex);
        } catch (IOException ioe) {
            Log.severe("Exception while writing JSON-file.", ioe);
        } finally {
            if(fos != null) try { fos.close(); } catch (IOException x) {}
            if(f.exists()) f.delete();
            fnew.renameTo(f);
        }
    }

    @Override
    public void triggered(DynmapWorld t) {
        /* Update markers for now-active world */
        dirty_worlds.add(t.getName());
    }

    /* Remove icon */
    static void removeIcon(MarkerIcon ico) {
        MarkerIcon def = api.getMarkerIcon(MarkerIcon.DEFAULT);
        /* Need to scrub all uses of this icon from markers */
        for(MarkerSet s : api.markersets.values()) {
            for(Marker m : s.getMarkers()) {
                if(m.getMarkerIcon() == ico) {
                    m.setMarkerIcon(def);    /* Set to default */
                }
            }
            Set<MarkerIcon> allowed = s.getAllowedMarkerIcons();
            if((allowed != null) && (allowed.contains(ico))) {
                s.removeAllowedMarkerIcon(ico);
            }
        }
        /* Remove files */
        File f = new File(api.markerdir, ico.getMarkerIconID() + ".png");
        f.delete();
        f = new File(api.markertiledir, ico.getMarkerIconID() + ".png");
        f.delete();
        
        /* Remove from marker icons */
        api.markericons.remove(ico.getMarkerIconID());
        saveMarkers();
    }
    /**
     * Test if given player can see another player on the map (based on player sets and privileges).
     * @param player - player attempting to observe
     * @param player_to_see - player to be observed by 'player'
     * @return true if can be seen on map, false if cannot be seen
     */
    public boolean testIfPlayerVisible(String player, String player_to_see)
    {
        if(api == null) return false;
        /* Go through player sets - see if any are applicable */
        for(Entry<String, PlayerSetImpl> s : playersets.entrySet()) {
            PlayerSetImpl ps = s.getValue();
            if(!ps.isPlayerInSet(player_to_see)) { /* Is in set? */
                continue;
            }
            if(ps.isSymmetricSet() && ps.isPlayerInSet(player)) {   /* If symmetric, and observer is there */
                return true;
            }
            if(core.checkPermission(player, "playerset." + s.getKey())) {   /* If player has privilege */
                return true;
            }
        }
        return false;
    }
    /**
     * Get set of player visible to given player
     */
    public Set<String> getPlayersVisibleToPlayer(String player) {
        player = player.toLowerCase();
        HashSet<String> pset = new HashSet<String>();
        pset.add(player);
        /* Go through player sets - see if any are applicable */
        for(Entry<String, PlayerSetImpl> s : playersets.entrySet()) {
            PlayerSetImpl ps = s.getValue();
            if(ps.isSymmetricSet() && ps.isPlayerInSet(player)) {   /* If symmetric, and observer is there */
                pset.addAll(ps.getPlayers());
            }
            else if(core.checkPermission(player, "playerset." + s.getKey())) {   /* If player has privilege */
                pset.addAll(ps.getPlayers());
            }
        }
        return pset;
    }
}
