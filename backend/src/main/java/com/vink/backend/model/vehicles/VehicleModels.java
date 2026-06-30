package com.vink.backend.model.vehicles;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Java port of server/src/types/vehicles.ts */
public class VehicleModels {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GpsPosition {
        private double lat;
        private double lng;
        private double altitude;
        private double accuracy;
        private double heading;
        private String timestamp;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SensorData {
        private double speedKph;
        private double fuelPercent;
        private double engineTempC;
        private boolean engineOn;
        private double odometreKm;
        private double rpm;
        private double batteryV;
        private boolean doorOpen;
        private boolean ignitionOn;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Vehicle {
        private String id;
        private String plateNumber;
        private String make;
        private String model;
        private int year;
        private String colour;
        private String vin;
        private String fleetGroup;
        private String driverId; // nullable
        private String status; // moving | idle | stopped | offline | maintenance
        private String commChannel; // cellular_4g | cellular_3g | satellite | offline
        private GpsPosition position;
        private SensorData sensors;
        private String lastSeen;
        private String registeredAt;
        private String simIccid;
        private String deviceId;
        private String insuranceExpiry;
        private String licenceExpiry;
        private double nextServiceKm;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Driver {
        private String id;
        private String name;
        private String licenceNo;
        private String phone;
        private String email;
        private String status; // active | inactive | suspended
        private String assignedVehicleId; // nullable
        private int totalTrips;
        private double totalKm;
        private int safetyScore;
        private String joinedAt;
        private String avatarInitials;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TripPoint {
        private double lat;
        private double lng;
        private double speedKph;
        private String timestamp;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Trip {
        private String id;
        private String vehicleId;
        private String driverId; // nullable
        private GpsPosition startPos;
        private GpsPosition endPos; // nullable
        private String startTime;
        private String endTime; // nullable
        private double distanceKm;
        private double avgSpeedKph;
        private double maxSpeedKph;
        private double fuelUsedL;
        private List<TripPoint> points;
        private boolean ongoing;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class VehicleAlert {
        private String id;
        private String vehicleId;
        private String plateNumber;
        private String driverId; // nullable
        private String type; // speeding | geofence_breach | low_fuel | harsh_braking | engine_off | sos | tampering | accident
        private String severity; // critical | warning | info
        private String message;
        private GpsPosition position;
        private String detectedAt;
        private String resolvedAt; // nullable
        private boolean acknowledged;
        private Double value; // nullable
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Geofence {
        private String id;
        private String name;
        private String description;
        private String shape; // circle | polygon
        private double centerLat;
        private double centerLng;
        private double radiusM;
        private boolean active;
        private String triggerOn; // enter | exit | both
        private List<String> alertVehicleGroups;
        private String createdAt;
        private int breachCount;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FleetSnapshot {
        private String timestamp;
        private long totalVehicles;
        private long movingNow;
        private long idleNow;
        private long offlineNow;
        private long maintenanceNow;
        private long activeAlerts;
        private double avgSpeedKph;
        private double avgFuelPercent;
        private double totalKmToday;
        private long activeDrivers;
        private long geofenceBreaches;
    }
}
