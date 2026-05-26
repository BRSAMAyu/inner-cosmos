package com.innercosmos.service;

public interface GravityService {
    double calculateGravity(double intensity, int recurrenceCount, double userImportance, int triggerCount, long daysSinceLastTouched);
}
