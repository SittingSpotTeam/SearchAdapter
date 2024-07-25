package com.sittingspot.searchadapter.DTO;

import com.sittingspot.searchadapter.models.Location;
import com.sittingspot.searchadapter.models.Tag;

import java.io.Serializable;
import java.util.List;

public record SittingSpotOutDTO(String id, Location location, List<Tag> tags,
                                List<String> labels) implements Serializable {
}