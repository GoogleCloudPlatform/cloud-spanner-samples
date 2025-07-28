package com.manolo.lor.spanner;

import java.util.ArrayList;
import java.util.Map;

public class LorEngineRequest {

    private String characters[];
    private String kinds[];
    private String places[];
    private int minStrength;
    private int maxStrength;

    public static final int MIN_STRENGTH = 1;
    public static final int MAX_STRENGTH = 533;

    LorEngineRequest(){    
    }

    public LorEngineRequest(Map<String, Object> payload){
        ArrayList<String> arrKinds = ((ArrayList)payload.get("kinds"));
        kinds = arrKinds.toArray(new String[0]);

        ArrayList<String> arrCharacters = ((ArrayList)payload.get("characters"));
        characters = arrCharacters.toArray(new String[0]);

        ArrayList<String> arrPlaces = ((ArrayList)payload.get("places"));
        places = arrPlaces.toArray(new String[0]);

        minStrength = ((Integer)payload.get("minStrenght"));
        maxStrength = ((Integer)payload.get("maxStrenght"));
    }

    public String[] getCharacters() {
        return characters;
    }

    public void setCharacters(String[] characters) {
        this.characters = characters;
    }

    public String[] getKinds() {
        return kinds;
    }

    public void setKinds(String[] kinds) {
        this.kinds = kinds;
    }

    public int getMinStrength() {
        return minStrength;
    }

    public void setMinStrength(int minStrength) {
        this.minStrength = minStrength;
    }

    public int getMaxStrength() {
        return maxStrength;
    }

    public void setMaxStrength(int maxStrength) {
        this.maxStrength = maxStrength;
    }

    public String[] getPlaces() {
        return places;
    }

    public void setPlaces(String[] places) {
        this.places = places;
    }



}
