package com.sittingspot.searchadapter.models;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OsmQueryResult {

    public record Osm3s(String timestamp_osm_base, String copyright){};
    public record OsmQueryElement(String type, double id, double lat, double lon, Map<String,String> tags){};

    private double version;
    private String generator;
    private Osm3s osm3s;
    private List<OsmQueryElement> elements;
}
