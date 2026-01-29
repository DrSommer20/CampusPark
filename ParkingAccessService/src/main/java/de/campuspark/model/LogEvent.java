package de.campuspark.model;

public record LogEvent(
    String level,     
    String source,      
    String message,    
    String spotId,      
    long timestamp      
) {}