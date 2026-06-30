package com.vink.backend.store;

import com.vink.backend.model.vehicles.VehicleModels.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Java port of server/src/data/vehicleStore.ts.
 * In-memory seeded dataset mirroring the original Express/TS demo store -
 * same field names, same generation logic, same record counts.
 */
@Component
public class VehicleStore {

    private static final double[] SA_BOUNDS = {-34.0, -22.0, 16.0, 33.0}; // latMin,latMax,lngMin,lngMax

    private record City(String name, double lat, double lng) {}

    private static final List<City> CITIES = List.of(
            new City("Johannesburg", -26.2041, 28.0473),
            new City("Cape Town", -33.9249, 18.4241),
            new City("Durban", -29.8587, 31.0218),
            new City("Pretoria", -25.7479, 28.2293),
            new City("Port Elizabeth", -33.9608, 25.6022),
            new City("Bloemfontein", -29.0852, 26.1596),
            new City("East London", -33.0153, 27.9116),
            new City("Polokwane", -23.9045, 29.4689)
    );

    private record MakeModels(String make, List<String> models) {}

    private static final List<MakeModels> MAKES = List.of(
            new MakeModels("Toyota", List.of("Hilux", "Land Cruiser", "Fortuner", "Quantum")),
            new MakeModels("Ford", List.of("Ranger", "Transit", "Everest")),
            new MakeModels("Isuzu", List.of("D-Max", "NLR 150", "NPR")),
            new MakeModels("VW", List.of("Transporter", "Amarok", "Crafter")),
            new MakeModels("Mercedes", List.of("Sprinter", "Vito", "Actros")),
            new MakeModels("Hino", List.of("300", "500", "700"))
    );

    private static final List<String> FLEETS = List.of("Delivery", "Executive", "Field Ops", "Security", "Maintenance");
    private static final List<String> COLOURS = List.of("White", "Silver", "Black", "Blue", "Red", "Orange");
    private static final List<String> STATUSES = List.of("moving", "moving", "moving", "idle", "idle", "stopped", "offline", "maintenance");
    private static final List<String> DRIVER_NAMES = List.of(
            "Sipho Nkosi", "Thabo Dlamini", "Lerato Mokoena", "Nomsa Khumalo", "David van der Berg",
            "Fatima Essop", "Johan Botha", "Priya Naidoo", "Bongani Zulu", "Elaine Mostert",
            "Khulekani Mthembu", "Ayanda Cele", "Pieter du Plessis", "Zanele Mahlangu", "Ravi Pillay",
            "Amina Mohamed", "Chris Steyn", "Nolwazi Dube", "Stefan Kruger", "Mmaphuti Mokgopa",
            "Lungelo Buthelezi", "Sonia Ferreira", "Thandeka Ntuli", "Wayne Jacobs", "Keabetswe Sithole",
            "Lebo Masondo", "Mark Thompson", "Zinhle Ndlovu", "André Fourie", "Busisiwe Mkhize",
            "Sanele Gumbi", "Rebecca Jooste", "Mthokozisi Ndaba", "Charlene van Wyk", "Nkosinathi Mhlanga",
            "Desiré Bosman", "Vusi Shabalala", "Lindi Pietersen", "Siyanda Majola", "Henk Swanepoel"
    );
    private static final List<String> PROVINCES = List.of("GP", "WC", "KZN", "EC", "FS", "LP");
    private static final List<String> COMM = List.of("cellular_4g", "cellular_4g", "cellular_4g", "cellular_3g", "satellite");
    private static final List<String> ALERT_TYPES = List.of("speeding", "geofence_breach", "low_fuel", "harsh_braking", "engine_off", "sos", "tampering", "accident");

    public final List<Vehicle> vehicles = new ArrayList<>();
    public final List<Driver> drivers = new ArrayList<>();
    public final List<Trip> trips = new ArrayList<>();
    public final List<VehicleAlert> alerts = new ArrayList<>();
    public final List<Geofence> geofences = new ArrayList<>();
    public final List<FleetSnapshot> fleetHistory = new ArrayList<>();

    public VehicleStore() {
        seed();
    }

    private static int rand(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static double randF(double min, double max) {
        double v = ThreadLocalRandom.current().nextDouble(min, max);
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static String ago(long minutes) {
        return Instant.now().minus(minutes, ChronoUnit.MINUTES).toString();
    }

    private static String inFuture(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).toString();
    }

    private City nearCity(int i) {
        return CITIES.get(i % CITIES.size());
    }

    private GpsPosition makePosition(int i) {
        City c = nearCity(i);
        GpsPosition p = new GpsPosition();
        p.setLat(Math.round((c.lat() + randF(-0.15, 0.15)) * 1_000_000.0) / 1_000_000.0);
        p.setLng(Math.round((c.lng() + randF(-0.15, 0.15)) * 1_000_000.0) / 1_000_000.0);
        p.setAltitude(rand(10, 1800));
        p.setAccuracy(rand(3, 15));
        p.setHeading(rand(0, 359));
        p.setTimestamp(ago(rand(0, 5)));
        return p;
    }

    private SensorData makeSensors(String status) {
        boolean moving = "moving".equals(status);
        SensorData s = new SensorData();
        s.setSpeedKph(moving ? rand(20, 120) : 0);
        s.setFuelPercent(rand(8, 98));
        s.setEngineTempC(moving ? rand(82, 105) : rand(20, 50));
        s.setEngineOn(!status.equals("offline") && !status.equals("stopped"));
        s.setOdometreKm(rand(5000, 250000));
        s.setRpm(moving ? rand(1200, 3800) : rand(0, 900));
        s.setBatteryV(randF(11.8, 14.4));
        s.setDoorOpen(ThreadLocalRandom.current().nextDouble() < 0.05);
        s.setIgnitionOn(status.equals("moving") || status.equals("idle"));
        return s;
    }

    private void seed() {
        // Vehicles
        for (int i = 0; i < 50; i++) {
            MakeModels mk = MAKES.get(i % MAKES.size());
            String model = mk.models().get(i % mk.models().size());
            String status = STATUSES.get(i % STATUSES.size());

            Vehicle v = new Vehicle();
            v.setId(UUID.randomUUID().toString());
            v.setPlateNumber(PROVINCES.get(i % 6) + " " + rand(100, 999) + " "
                    + (char) ('A' + (i % 26)) + (char) ('A' + ((i + 3) % 26)));
            v.setMake(mk.make());
            v.setModel(model);
            v.setYear(rand(2016, 2024));
            v.setColour(COLOURS.get(i % COLOURS.size()));
            v.setVin("VIN" + String.format("%014d", i));
            v.setFleetGroup(FLEETS.get(i % FLEETS.size()));
            v.setDriverId(!status.equals("offline") ? "DRV-" + String.format("%03d", i) : null);
            v.setStatus(status);
            v.setCommChannel(COMM.get(i % COMM.size()));
            v.setPosition(makePosition(i));
            v.setSensors(makeSensors(status));
            v.setLastSeen(ago(status.equals("offline") ? rand(30, 480) : rand(0, 3)));
            v.setRegisteredAt(ago(rand(1440, 525600)));
            v.setSimIccid("8927" + String.format("%015d", i));
            v.setDeviceId("GT06N-" + String.format("%06d", i));
            v.setInsuranceExpiry(inFuture(rand(30, 365)));
            v.setLicenceExpiry(inFuture(rand(60, 730)));
            v.setNextServiceKm(rand(1000, 10000));
            vehicles.add(v);
        }

        // Drivers
        for (int i = 0; i < 40; i++) {
            Driver d = new Driver();
            d.setId("DRV-" + String.format("%03d", i));
            d.setName(DRIVER_NAMES.get(i));
            d.setLicenceNo("SA" + rand(1000000, 9999999));
            d.setPhone("+2760" + rand(1000000, 9999999));
            d.setEmail("driver" + i + "@vink-fleet.co.za");
            d.setStatus(i % 8 == 0 ? "suspended" : "active");
            d.setAssignedVehicleId(null);
            d.setTotalTrips(rand(50, 1200));
            d.setTotalKm(rand(5000, 180000));
            d.setSafetyScore(rand(55, 100));
            d.setJoinedAt(ago(rand(1440, 525600)));
            String[] parts = d.getName().split(" ");
            StringBuilder initials = new StringBuilder();
            for (String p : parts) if (!p.isEmpty()) initials.append(p.charAt(0));
            d.setAvatarInitials(initials.toString().toUpperCase().substring(0, Math.min(2, initials.length())));
            drivers.add(d);
        }

        // Trips
        for (int i = 0; i < 80; i++) {
            int vehicleIdx = i % 50;
            City cityA = CITIES.get(i % CITIES.size());
            City cityB = CITIES.get((i + 1) % CITIES.size());
            String start = ago(rand(30, 1440));
            boolean ended = i % 6 != 0;
            double dist = randF(5, 420);
            int avgSpd = rand(35, 95);

            List<TripPoint> points = new ArrayList<>();
            Instant startInstant = Instant.parse(start);
            for (int p = 0; p < 8; p++) {
                TripPoint tp = new TripPoint();
                tp.setLat(Math.round((cityA.lat() + (cityB.lat() - cityA.lat()) * (p / 7.0) + randF(-0.02, 0.02)) * 1_000_000.0) / 1_000_000.0);
                tp.setLng(Math.round((cityA.lng() + (cityB.lng() - cityA.lng()) * (p / 7.0) + randF(-0.02, 0.02)) * 1_000_000.0) / 1_000_000.0);
                tp.setSpeedKph(rand(0, 130));
                tp.setTimestamp(startInstant.plusMillis((long) p * rand(180000, 600000)).toString());
                points.add(tp);
            }

            Trip t = new Trip();
            t.setId(UUID.randomUUID().toString());
            t.setVehicleId("VEH-PLACEHOLDER-" + vehicleIdx); // patched below
            t.setDriverId("DRV-" + String.format("%03d", vehicleIdx % 40));

            GpsPosition startPos = new GpsPosition();
            startPos.setLat(cityA.lat()); startPos.setLng(cityA.lng());
            startPos.setAltitude(1200); startPos.setAccuracy(5);
            startPos.setHeading(rand(0, 359)); startPos.setTimestamp(start);
            t.setStartPos(startPos);

            if (ended) {
                GpsPosition endPos = new GpsPosition();
                endPos.setLat(cityB.lat()); endPos.setLng(cityB.lng());
                endPos.setAltitude(1100); endPos.setAccuracy(5);
                endPos.setHeading(rand(0, 359)); endPos.setTimestamp(ago(rand(0, 120)));
                t.setEndPos(endPos);
                t.setEndTime(ago(rand(0, 120)));
            }
            t.setStartTime(start);
            t.setDistanceKm(dist);
            t.setAvgSpeedKph(avgSpd);
            t.setMaxSpeedKph(Math.min(rand(avgSpd + 10, 160), 160));
            t.setFuelUsedL(Math.round(dist * randF(0.06, 0.12) * 100.0) / 100.0);
            t.setPoints(points);
            t.setOngoing(!ended);
            trips.add(t);
        }

        // Alerts
        for (int i = 0; i < 30; i++) {
            String type = ALERT_TYPES.get(i % ALERT_TYPES.size());
            String sev = (type.equals("sos") || type.equals("accident")) ? "critical"
                    : (type.equals("speeding") || type.equals("tampering")) ? "warning" : "info";
            GpsPosition pos = makePosition(i);

            VehicleAlert a = new VehicleAlert();
            a.setId(UUID.randomUUID().toString());
            a.setVehicleId("VEH-PLACEHOLDER-" + (i % 50)); // patched below
            a.setPlateNumber("GP " + rand(100, 999) + " AB");
            a.setDriverId("DRV-" + String.format("%03d", i % 40));
            a.setType(type);
            a.setSeverity(sev);
            a.setMessage(switch (type) {
                case "speeding" -> "Vehicle exceeded speed limit — " + rand(80, 160) + " km/h in 60 zone";
                case "geofence_breach" -> "Vehicle exited authorised geofence boundary";
                case "low_fuel" -> "Fuel level critically low — " + rand(5, 15) + "% remaining";
                case "harsh_braking" -> "Harsh braking event detected at " + rand(60, 110) + " km/h";
                case "engine_off" -> "Engine switched off outside designated area";
                case "sos" -> "SOS panic button activated by driver";
                case "tampering" -> "GPS device tampering detected";
                case "accident" -> "Possible accident — sudden deceleration to 0 km/h";
                default -> "";
            });
            a.setPosition(pos);
            a.setDetectedAt(ago(rand(0, 480)));
            a.setResolvedAt(i % 4 == 0 ? ago(rand(0, 60)) : null);
            a.setAcknowledged(i % 3 == 0);
            a.setValue(type.equals("speeding") ? (double) rand(80, 160) : type.equals("low_fuel") ? (double) rand(5, 15) : null);
            alerts.add(a);
        }

        // Geofences
        geofences.addAll(List.of(
                geofence("Johannesburg HQ", "Head office compound", -26.2041, 28.0473, 500, true, "exit", List.of("Delivery", "Executive"), 43800, 12),
                geofence("Cape Town Depot", "Western Cape warehouse", -33.9249, 18.4241, 800, true, "both", List.of("Delivery"), 20160, 7),
                geofence("Durban Port Zone", "Port operations area", -29.8587, 31.0218, 1200, true, "enter", List.of("Field Ops", "Delivery"), 10080, 3),
                geofence("Pretoria Admin Zone", "Government precinct", -25.7479, 28.2293, 600, true, "exit", List.of("Executive"), 5040, 2),
                geofence("Night Curfew Area", "After-hours restricted", -26.1052, 28.0560, 3000, false, "enter", List.of("Delivery", "Field Ops"), 2880, 0)
        ));

        // Fleet history (24 hourly snapshots)
        for (int i = 0; i < 24; i++) {
            FleetSnapshot fs = new FleetSnapshot();
            fs.setTimestamp(ago((23 - i) * 60L));
            fs.setTotalVehicles(50);
            fs.setMovingNow(rand(18, 32));
            fs.setIdleNow(rand(5, 12));
            fs.setOfflineNow(rand(2, 6));
            fs.setMaintenanceNow(rand(1, 4));
            fs.setActiveAlerts(rand(3, 18));
            fs.setAvgSpeedKph(rand(45, 78));
            fs.setAvgFuelPercent(rand(52, 72));
            fs.setTotalKmToday(rand(800, 4200));
            fs.setActiveDrivers(rand(20, 35));
            fs.setGeofenceBreaches(rand(0, 5));
            fleetHistory.add(fs);
        }

        // Patch trip vehicle ids to real vehicle ids
        for (int i = 0; i < trips.size(); i++) {
            trips.get(i).setVehicleId(vehicles.get(i % vehicles.size()).getId());
        }

        // Patch alert vehicle/driver/plate to real vehicle data
        for (int i = 0; i < alerts.size(); i++) {
            Vehicle v = vehicles.get(i % vehicles.size());
            VehicleAlert a = alerts.get(i);
            a.setVehicleId(v.getId());
            a.setPlateNumber(v.getPlateNumber());
            a.setDriverId(v.getDriverId());
        }
    }

    private Geofence geofence(String name, String description, double lat, double lng, double radius,
                               boolean active, String triggerOn, List<String> groups, long ageMinutes, int breachCount) {
        Geofence g = new Geofence();
        g.setId(UUID.randomUUID().toString());
        g.setName(name);
        g.setDescription(description);
        g.setShape("circle");
        g.setCenterLat(lat);
        g.setCenterLng(lng);
        g.setRadiusM(radius);
        g.setActive(active);
        g.setTriggerOn(triggerOn);
        g.setAlertVehicleGroups(groups);
        g.setCreatedAt(ago(ageMinutes));
        g.setBreachCount(breachCount);
        return g;
    }

    /** Java port of getFleetSnapshot() in vehicleStore.ts */
    public FleetSnapshot getFleetSnapshot() {
        FleetSnapshot fs = new FleetSnapshot();
        fs.setTimestamp(Instant.now().toString());
        fs.setTotalVehicles(vehicles.size());
        fs.setMovingNow(vehicles.stream().filter(v -> v.getStatus().equals("moving")).count());
        fs.setIdleNow(vehicles.stream().filter(v -> v.getStatus().equals("idle")).count());
        fs.setOfflineNow(vehicles.stream().filter(v -> v.getStatus().equals("offline")).count());
        fs.setMaintenanceNow(vehicles.stream().filter(v -> v.getStatus().equals("maintenance")).count());
        fs.setActiveAlerts(alerts.stream().filter(a -> a.getResolvedAt() == null).count());

        List<Vehicle> movingVehicles = vehicles.stream().filter(v -> v.getStatus().equals("moving")).collect(Collectors.toList());
        double avgSpeed = movingVehicles.stream().mapToDouble(v -> v.getSensors().getSpeedKph()).average().orElse(0);
        fs.setAvgSpeedKph(Math.round(avgSpeed * 10.0) / 10.0);

        double avgFuel = vehicles.stream().mapToDouble(v -> v.getSensors().getFuelPercent()).average().orElse(0);
        fs.setAvgFuelPercent(Math.round(avgFuel * 10.0) / 10.0);

        String cutoff = ago(1440);
        double kmToday = trips.stream().filter(t -> t.getStartTime().compareTo(cutoff) > 0).mapToDouble(Trip::getDistanceKm).sum();
        fs.setTotalKmToday(Math.round(kmToday * 10.0) / 10.0);

        long activeDrivers = drivers.stream().filter(d -> d.getStatus().equals("active")
                && vehicles.stream().anyMatch(v -> d.getId().equals(v.getDriverId()) && v.getStatus().equals("moving"))).count();
        fs.setActiveDrivers(activeDrivers);

        long geofenceBreaches = alerts.stream().filter(a -> a.getType().equals("geofence_breach") && a.getResolvedAt() == null).count();
        fs.setGeofenceBreaches(geofenceBreaches);

        return fs;
    }

    public Optional<Vehicle> findVehicle(String id) {
        return vehicles.stream().filter(v -> v.getId().equals(id)).findFirst();
    }

    public Optional<Driver> findDriver(String id) {
        return drivers.stream().filter(d -> d.getId().equals(id)).findFirst();
    }

    public Optional<Geofence> findGeofence(String id) {
        return geofences.stream().filter(g -> g.getId().equals(id)).findFirst();
    }

    public Optional<VehicleAlert> findAlert(String id) {
        return alerts.stream().filter(a -> a.getId().equals(id)).findFirst();
    }
}
