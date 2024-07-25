package com.sittingspot.searchadapter.DTO;

import com.sittingspot.searchadapter.models.Location;
import com.sittingspot.searchadapter.models.Tag;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public record SittingSpotInDTO(String id, Location location, List<Tag> tags) implements Serializable {
}