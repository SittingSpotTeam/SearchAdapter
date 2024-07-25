package com.sittingspot.searchadapter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sittingspot.searchadapter.DTO.SittingSpotInDTO;
import com.sittingspot.searchadapter.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@RestController("/search-adapter/api/v1")
public class SearchAdapterController {

    @Value("${sittingspot.sittingspotdl.url}")
    private String sittingspotdlUrl;

    @Value("${sittingspot.sittingspotdl.api.version}")
    private String sittingspotdlApiVersion;

    @Value("${sittingspot.osm.host}")
    private String osmHost;

    @Value("${sittingspot.osm.port}")
    private String osmPort;

    @Value("${sittingspot.osm.overpass.api}")
    private String osmOverpassApi;

    @GetMapping("/")
    public List<QueryResult> search(@RequestParam("location") Area location,
                                    @RequestParam(value = "tags",required = false) List<Tag> tags,
                                    @RequestParam(value = "labels",required = false) List<String> labels) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();

        List<SittingSpotInDTO> osmData;

        if(labels.isEmpty()) {
            StringBuilder osmQuery = new StringBuilder("[out:json];\nnode[amenity=bench]");
            for(var tag : tags) {
                osmQuery.append("[").append(tag.key()).append("=").append(tag.value()).append("]");
            }
            osmQuery.append("(around:" + location.range() + "," + location.center().x() + ","+location.center().y()+");\n" +"out body;");

            var osmRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + osmHost + ":" + osmPort + "/" + osmOverpassApi))
                    .POST(HttpRequest.BodyPublishers.ofString(osmQuery.toString())).build();

            var osmResult = client.send(osmRequest, HttpResponse.BodyHandlers.ofString());

            // TODO: check osm return codes

            if(osmResult.statusCode() == 200) {
                OsmQueryResult data = new ObjectMapper().readValue(osmResult.body(), OsmQueryResult.class);
                // to check if lat = x and lon = y or viceversa

                // convert list of elements to list of sitting spot
                osmData = data.getElements()
                        .stream()
                        .map(x -> new SittingSpotInDTO(x.id(), new Location(x.lat(),x.lon()), x.tags()
                                .entrySet()
                                .stream()
                                .map(t -> new Tag(t.getKey(), t.getValue()))
                                .toList()))
                        .toList();
            }
        }

        var dlRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://" + sittingspotdlUrl + sittingspotdlApiVersion + "/find" + "?location="+location+"&tags="+tags+"&labels="+labels)).GET().build();

        return List.of();
    }
}
