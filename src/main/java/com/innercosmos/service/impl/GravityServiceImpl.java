package com.innercosmos.service.impl;

import com.innercosmos.service.GravityService;
import org.springframework.stereotype.Service;

@Service
public class GravityServiceImpl implements GravityService {
    @Override
    public double calculateGravity(double intensity, int recurrenceCount, double userImportance, int triggerCount, long daysSinceLastTouched) {
        double alpha = 0.40;
        double beta = 0.25;
        double gamma = 0.25;
        double delta = 0.10;
        double lambda = 0.05;
        double base = alpha * intensity + beta * recurrenceCount + gamma * userImportance + delta * triggerCount;
        return Math.log(1 + Math.max(base, 0)) * Math.exp(-lambda * Math.max(daysSinceLastTouched, 0));
    }
}
