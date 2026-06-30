package com.vink.backend.controller;

import com.vink.backend.middleware.AuthGuard;
import com.vink.backend.model.ApiResponse;
import com.vink.backend.model.AuthPayload;
import com.vink.backend.model.vehicles.VehicleModels.*;
import com.vink.backend.store.VehicleStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Java port of server/src/routes/vehicles.ts.
 * Mounted at /api/vehicles to mirror the original Express router mount point
 * (see server/src/index.ts: app.use("/api/vehicles", vehiclesRouter)).
 */
@RestController
@RequestMapping("/api/vehicles")
public class VehiclesController {

    private final VehicleStore db;

    public VehiclesController(VehicleStore db) {
        this.db = db;
    }

    // ─── Fleet / Vehicles ───────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest req,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String fleet,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer limit) {
        AuthGuard.requireAuth(req);
        int p = Math.max(1, page == null ? 1 : page);
        int l = Math.min(100, limit == null ? 20 : limit);

        List<Vehicle> data = new ArrayList<>(db.vehicles);
        if (status != null) data = data.stream().filter(v -> v.getStatus().equals(status)).collect(Collectors.toList());
        if (fleet != null) data = data.stream().filter(v -> v.getFleetGroup().equalsIgnoreCase(fleet)).collect(Collectors.toList());
        if (search != null) {
            String q = search.toLowerCase();
            data = data.stream().filter(v -> v.getPlateNumber().toLowerCase().contains(q)
                    || v.getMake().toLowerCase().contains(q) || v.getModel().toLowerCase().contains(q)).collect(Collectors.toList());
        }
        int total = data.size();
        List<Vehicle> page1 = paginate(data, p, l);
        return ResponseEntity.ok(ApiResponse.ok(page1, ApiResponse.meta("page", p, "limit", l, "total", total, "pages", pages(total, l))));
    }

    @GetMapping("/snapshot")
    public ResponseEntity<?> snapshot(HttpServletRequest req) {
        AuthGuard.requireAuth(req);
        return ResponseEntity.ok(ApiResponse.ok(db.getFleetSnapshot()));
    }

    @GetMapping("/positions")
    public ResponseEntity<?> positions(HttpServletRequest req) {
        AuthGuard.requireAuth(req);
        List<Map<String, Object>> data = db.vehicles.stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("plateNumber", v.getPlateNumber());
            m.put("make", v.getMake());
            m.put("model", v.getModel());
            m.put("fleetGroup", v.getFleetGroup());
            m.put("driverId", v.getDriverId());
            m.put("status", v.getStatus());
            m.put("position", v.getPosition());
            m.put("speedKph", v.getSensors().getSpeedKph());
            m.put("fuelPercent", v.getSensors().getFuelPercent());
            m.put("lastSeen", v.getLastSeen());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(HttpServletRequest req, @PathVariable String id) {
        AuthGuard.requireAuth(req);
        Optional<Vehicle> found = db.vehicles.stream().filter(v -> v.getId().equals(id) || v.getPlateNumber().equals(id)).findFirst();
        if (found.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Vehicle not found"));
        Vehicle v = found.get();
        Driver driver = db.drivers.stream().filter(d -> d.getId().equals(v.getDriverId())).findFirst().orElse(null);
        List<VehicleAlert> activeAlerts = db.alerts.stream().filter(a -> a.getVehicleId().equals(v.getId()) && a.getResolvedAt() == null).collect(Collectors.toList());
        List<Trip> recentTrips = db.trips.stream().filter(t -> t.getVehicleId().equals(v.getId())).limit(5).collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", v.getId()); out.put("plateNumber", v.getPlateNumber()); out.put("make", v.getMake());
        out.put("model", v.getModel()); out.put("year", v.getYear()); out.put("colour", v.getColour());
        out.put("vin", v.getVin()); out.put("fleetGroup", v.getFleetGroup()); out.put("driverId", v.getDriverId());
        out.put("status", v.getStatus()); out.put("commChannel", v.getCommChannel()); out.put("position", v.getPosition());
        out.put("sensors", v.getSensors()); out.put("lastSeen", v.getLastSeen()); out.put("registeredAt", v.getRegisteredAt());
        out.put("simIccid", v.getSimIccid()); out.put("deviceId", v.getDeviceId()); out.put("insuranceExpiry", v.getInsuranceExpiry());
        out.put("licenceExpiry", v.getLicenceExpiry()); out.put("nextServiceKm", v.getNextServiceKm());
        out.put("driver", driver); out.put("activeAlerts", activeAlerts); out.put("recentTrips", recentTrips);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        AuthPayload user = AuthGuard.requireAuth(req);
        AuthGuard.requireRole(user, "superadmin", "noc_engineer");

        String plateNumber = (String) body.get("plateNumber");
        String make = (String) body.get("make");
        String model = (String) body.get("model");
        String vin = (String) body.get("vin");
        if (isBlank(plateNumber) || isBlank(make) || isBlank(model) || isBlank(vin)) {
            return ResponseEntity.status(400).body(ApiResponse.fail("plateNumber, make, model and vin are required"));
        }
        boolean clash = db.vehicles.stream().anyMatch(v -> v.getPlateNumber().equals(plateNumber) || v.getVin().equals(vin));
        if (clash) return ResponseEntity.status(409).body(ApiResponse.fail("Plate number or VIN already registered"));

        Vehicle v = new Vehicle();
        v.setId(UUID.randomUUID().toString());
        v.setPlateNumber(plateNumber);
        v.setMake(make);
        v.setModel(model);
        v.setYear(body.get("year") != null ? ((Number) body.get("year")).intValue() : Instant.now().atZone(java.time.ZoneOffset.UTC).getYear());
        v.setColour(body.getOrDefault("colour", "White").toString());
        v.setVin(vin);
        v.setFleetGroup(body.getOrDefault("fleetGroup", "General").toString());
        v.setDriverId(null);
        v.setStatus("stopped");
        v.setCommChannel("cellular_4g");

        GpsPosition pos = new GpsPosition();
        pos.setLat(-26.2041); pos.setLng(28.0473); pos.setAltitude(1753); pos.setAccuracy(5); pos.setHeading(0);
        pos.setTimestamp(Instant.now().toString());
        v.setPosition(pos);

        SensorData sensors = new SensorData();
        sensors.setSpeedKph(0); sensors.setFuelPercent(100); sensors.setEngineTempC(20); sensors.setEngineOn(false);
        sensors.setOdometreKm(0); sensors.setRpm(0); sensors.setBatteryV(12.6); sensors.setDoorOpen(false); sensors.setIgnitionOn(false);
        v.setSensors(sensors);

        v.setLastSeen(Instant.now().toString());
        v.setRegisteredAt(Instant.now().toString());
        v.setSimIccid(body.getOrDefault("simIccid", "").toString());
        v.setDeviceId(body.get("deviceId") != null ? body.get("deviceId").toString() : UUID.randomUUID().toString());
        v.setInsuranceExpiry(body.get("insuranceExpiry") != null ? body.get("insuranceExpiry").toString() : Instant.now().plusSeconds(365L * 86400).toString());
        v.setLicenceExpiry(body.get("licenceExpiry") != null ? body.get("licenceExpiry").toString() : Instant.now().plusSeconds(365L * 86400).toString());
        v.setNextServiceKm(body.get("nextServiceKm") != null ? ((Number) body.get("nextServiceKm")).doubleValue() : 10000);

        db.vehicles.add(v);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(v));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(HttpServletRequest req, @PathVariable String id, @RequestBody Map<String, Object> body) {
        AuthPayload user = AuthGuard.requireAuth(req);
        AuthGuard.requireRole(user, "superadmin", "noc_engineer");
        Optional<Vehicle> found = db.findVehicle(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Vehicle not found"));
        Vehicle v = found.get();
        if (body.containsKey("status")) v.setStatus((String) body.get("status"));
        if (body.containsKey("driverId")) v.setDriverId((String) body.get("driverId"));
        if (body.containsKey("fleetGroup")) v.setFleetGroup((String) body.get("fleetGroup"));
        if (body.containsKey("insuranceExpiry")) v.setInsuranceExpiry((String) body.get("insuranceExpiry"));
        if (body.containsKey("licenceExpiry")) v.setLicenceExpiry((String) body.get("licenceExpiry"));
        if (body.containsKey("nextServiceKm")) v.setNextServiceKm(((Number) body.get("nextServiceKm")).doubleValue());
        if (body.containsKey("commChannel")) v.setCommChannel((String) body.get("commChannel"));
        return ResponseEntity.ok(ApiResponse.ok(v));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> decommission(HttpServletRequest req, @PathVariable String id) {
        AuthPayload user = AuthGuard.requireAuth(req);
        AuthGuard.requireRole(user, "superadmin");
        Optional<Vehicle> found = db.findVehicle(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Vehicle not found"));
        Vehicle v = found.get();
        v.setStatus("offline");
        v.setDriverId(null);
        return ResponseEntity.ok(ApiResponse.msg("Vehicle decommissioned"));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> history(HttpServletRequest req, @PathVariable String id, @RequestParam(required = false) Integer limit) {
        AuthGuard.requireAuth(req);
        Optional<Vehicle> found = db.findVehicle(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Vehicle not found"));
        int l = Math.min(200, limit == null ? 50 : limit);
        List<Trip> trips = db.trips.stream().filter(t -> t.getVehicleId().equals(id)).limit(l).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(trips));
    }

    // ─── Drivers ────────────────────────────────────────────────────────

    @GetMapping("/drivers/all")
    public ResponseEntity<?> drivers(HttpServletRequest req, @RequestParam(required = false) String status) {
        AuthGuard.requireAuth(req);
        List<Driver> data = new ArrayList<>(db.drivers);
        if (status != null) data = data.stream().filter(d -> d.getStatus().equals(status)).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data, ApiResponse.meta("total", data.size())));
    }

    @GetMapping("/drivers/{id}")
    public ResponseEntity<?> driver(HttpServletRequest req, @PathVariable String id) {
        AuthGuard.requireAuth(req);
        Optional<Driver> found = db.findDriver(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Driver not found"));
        Driver d = found.get();
        Vehicle vehicle = db.vehicles.stream().filter(v -> id.equals(v.getDriverId())).findFirst().orElse(null);
        List<Trip> trips = db.trips.stream().filter(t -> id.equals(t.getDriverId())).limit(10).collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.getId()); out.put("name", d.getName()); out.put("licenceNo", d.getLicenceNo());
        out.put("phone", d.getPhone()); out.put("email", d.getEmail()); out.put("status", d.getStatus());
        out.put("assignedVehicleId", d.getAssignedVehicleId()); out.put("totalTrips", d.getTotalTrips());
        out.put("totalKm", d.getTotalKm()); out.put("safetyScore", d.getSafetyScore());
        out.put("joinedAt", d.getJoinedAt()); out.put("avatarInitials", d.getAvatarInitials());
        out.put("vehicle", vehicle); out.put("recentTrips", trips);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @PatchMapping("/drivers/{id}/assign")
    public ResponseEntity<?> assignDriver(HttpServletRequest req, @PathVariable String id, @RequestBody Map<String, Object> body) {
        AuthPayload user = AuthGuard.requireAuth(req);
        AuthGuard.requireRole(user, "superadmin", "noc_engineer");
        Optional<Driver> foundDriver = db.findDriver(id);
        if (foundDriver.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Driver not found"));
        Object vehicleId = body.get("vehicleId");
        Optional<Vehicle> foundVehicle = vehicleId == null ? Optional.empty() : db.findVehicle(vehicleId.toString());
        if (foundVehicle.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Vehicle not found"));
        Driver d = foundDriver.get();
        Vehicle v = foundVehicle.get();
        db.vehicles.stream().filter(x -> id.equals(x.getDriverId())).forEach(x -> x.setDriverId(null));
        v.setDriverId(d.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("driver", d); out.put("vehicle", v);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    // ─── Alerts ─────────────────────────────────────────────────────────

    @GetMapping("/alerts/all")
    public ResponseEntity<?> alerts(HttpServletRequest req,
                                     @RequestParam(required = false) String type,
                                     @RequestParam(required = false) String severity,
                                     @RequestParam(required = false) String resolved,
                                     @RequestParam(required = false) String vehicleId,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer limit) {
        AuthGuard.requireAuth(req);
        int p = Math.max(1, page == null ? 1 : page);
        int l = Math.min(100, limit == null ? 20 : limit);

        List<VehicleAlert> data = new ArrayList<>(db.alerts);
        if (type != null) data = data.stream().filter(a -> a.getType().equals(type)).collect(Collectors.toList());
        if (severity != null) data = data.stream().filter(a -> a.getSeverity().equals(severity)).collect(Collectors.toList());
        if (vehicleId != null) data = data.stream().filter(a -> a.getVehicleId().equals(vehicleId)).collect(Collectors.toList());
        if ("true".equals(resolved)) data = data.stream().filter(a -> a.getResolvedAt() != null).collect(Collectors.toList());
        if ("false".equals(resolved)) data = data.stream().filter(a -> a.getResolvedAt() == null).collect(Collectors.toList());

        data.sort((a, b) -> b.getDetectedAt().compareTo(a.getDetectedAt()));
        int total = data.size();
        long active = db.alerts.stream().filter(a -> a.getResolvedAt() == null).count();
        return ResponseEntity.ok(ApiResponse.ok(paginate(data, p, l),
                ApiResponse.meta("page", p, "limit", l, "total", total, "pages", pages(total, l), "active", active)));
    }

    @PatchMapping("/alerts/{id}/acknowledge")
    public ResponseEntity<?> acknowledge(HttpServletRequest req, @PathVariable String id) {
        AuthGuard.requireAuth(req);
        Optional<VehicleAlert> found = db.findAlert(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Alert not found"));
        found.get().setAcknowledged(true);
        return ResponseEntity.ok(ApiResponse.ok(found.get()));
    }

    @PatchMapping("/alerts/{id}/resolve")
    public ResponseEntity<?> resolve(HttpServletRequest req, @PathVariable String id) {
        AuthPayload user = AuthGuard.requireAuth(req);
        AuthGuard.requireRole(user, "superadmin", "noc_engineer");
        Optional<VehicleAlert> found = db.findAlert(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Alert not found"));
        VehicleAlert a = found.get();
        a.setResolvedAt(Instant.now().toString());
        a.setAcknowledged(true);
        return ResponseEntity.ok(ApiResponse.ok(a));
    }

    // ─── Geofences ──────────────────────────────────────────────────────

    @GetMapping("/geofences/all")
    public ResponseEntity<?> geofences(HttpServletRequest req) {
        AuthGuard.requireAuth(req);
        return ResponseEntity.ok(ApiResponse.ok(db.geofences, ApiResponse.meta("total", db.geofences.size())));
    }

    @PostMapping("/geofences")
    public ResponseEntity<?> createGeofence(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        AuthPayload user = AuthGuard.requireAuth(req);
        AuthGuard.requireRole(user, "superadmin", "noc_engineer");
        String name = (String) body.get("name");
        Object centerLat = body.get("centerLat");
        Object centerLng = body.get("centerLng");
        Object radiusM = body.get("radiusM");
        if (isBlank(name) || centerLat == null || centerLng == null || radiusM == null) {
            return ResponseEntity.status(400).body(ApiResponse.fail("name, centerLat, centerLng and radiusM are required"));
        }
        Geofence gf = new Geofence();
        gf.setId(UUID.randomUUID().toString());
        gf.setName(name);
        gf.setDescription(body.getOrDefault("description", "").toString());
        gf.setShape("circle");
        gf.setCenterLat(((Number) centerLat).doubleValue());
        gf.setCenterLng(((Number) centerLng).doubleValue());
        gf.setRadiusM(((Number) radiusM).doubleValue());
        gf.setActive(true);
        gf.setTriggerOn(body.getOrDefault("triggerOn", "both").toString());
        @SuppressWarnings("unchecked")
        List<String> groups = body.get("alertVehicleGroups") != null ? (List<String>) body.get("alertVehicleGroups") : List.of();
        gf.setAlertVehicleGroups(groups);
        gf.setCreatedAt(Instant.now().toString());
        gf.setBreachCount(0);
        db.geofences.add(gf);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(gf));
    }

    @PatchMapping("/geofences/{id}")
    public ResponseEntity<?> patchGeofence(HttpServletRequest req, @PathVariable String id, @RequestBody Map<String, Object> body) {
        AuthPayload user = AuthGuard.requireAuth(req);
        AuthGuard.requireRole(user, "superadmin", "noc_engineer");
        Optional<Geofence> found = db.findGeofence(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Geofence not found"));
        Geofence gf = found.get();
        if (body.containsKey("name")) gf.setName((String) body.get("name"));
        if (body.containsKey("description")) gf.setDescription((String) body.get("description"));
        if (body.containsKey("radiusM")) gf.setRadiusM(((Number) body.get("radiusM")).doubleValue());
        if (body.containsKey("active")) gf.setActive((Boolean) body.get("active"));
        if (body.containsKey("triggerOn")) gf.setTriggerOn((String) body.get("triggerOn"));
        return ResponseEntity.ok(ApiResponse.ok(gf));
    }

    @DeleteMapping("/geofences/{id}")
    public ResponseEntity<?> deleteGeofence(HttpServletRequest req, @PathVariable String id) {
        AuthPayload user = AuthGuard.requireAuth(req);
        AuthGuard.requireRole(user, "superadmin");
        boolean removed = db.geofences.removeIf(g -> g.getId().equals(id));
        if (!removed) return ResponseEntity.status(404).body(ApiResponse.fail("Geofence not found"));
        return ResponseEntity.ok(ApiResponse.msg("Geofence deleted"));
    }

    // ─── Trips ──────────────────────────────────────────────────────────

    @GetMapping("/trips/all")
    public ResponseEntity<?> trips(HttpServletRequest req,
                                    @RequestParam(required = false) String vehicleId,
                                    @RequestParam(required = false) String driverId,
                                    @RequestParam(required = false) String ongoing,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer limit) {
        AuthGuard.requireAuth(req);
        int p = Math.max(1, page == null ? 1 : page);
        int l = Math.min(50, limit == null ? 20 : limit);

        List<Trip> data = new ArrayList<>(db.trips);
        if (vehicleId != null) data = data.stream().filter(t -> t.getVehicleId().equals(vehicleId)).collect(Collectors.toList());
        if (driverId != null) data = data.stream().filter(t -> driverId.equals(t.getDriverId())).collect(Collectors.toList());
        if ("true".equals(ongoing)) data = data.stream().filter(Trip::isOngoing).collect(Collectors.toList());
        if ("false".equals(ongoing)) data = data.stream().filter(t -> !t.isOngoing()).collect(Collectors.toList());

        data.sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));
        int total = data.size();
        return ResponseEntity.ok(ApiResponse.ok(paginate(data, p, l), ApiResponse.meta("page", p, "limit", l, "total", total, "pages", pages(total, l))));
    }

    // ─── Analytics ──────────────────────────────────────────────────────

    @GetMapping("/analytics/summary")
    public ResponseEntity<?> analyticsSummary(HttpServletRequest req) {
        AuthGuard.requireAuth(req);
        List<Vehicle> vv = db.vehicles;

        List<Map<String, Object>> byFleet = List.of("Delivery", "Executive", "Field Ops", "Security", "Maintenance").stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fleet", f);
            m.put("count", vv.stream().filter(v -> v.getFleetGroup().equals(f)).count());
            m.put("moving", vv.stream().filter(v -> v.getFleetGroup().equals(f) && v.getStatus().equals("moving")).count());
            m.put("offline", vv.stream().filter(v -> v.getFleetGroup().equals(f) && v.getStatus().equals("offline")).count());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> byStatus = List.of("moving", "idle", "stopped", "offline", "maintenance").stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", s);
            m.put("count", vv.stream().filter(v -> v.getStatus().equals(s)).count());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> alertsByType = List.of("speeding", "geofence_breach", "low_fuel", "harsh_braking", "engine_off", "sos", "tampering", "accident").stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", t);
            m.put("count", db.alerts.stream().filter(a -> a.getType().equals(t)).count());
            m.put("active", db.alerts.stream().filter(a -> a.getType().equals(t) && a.getResolvedAt() == null).count());
            return m;
        }).collect(Collectors.toList());

        FleetSnapshot snapshot = db.getFleetSnapshot();
        List<FleetSnapshot> history = db.fleetHistory.size() <= 24 ? db.fleetHistory
                : db.fleetHistory.subList(db.fleetHistory.size() - 24, db.fleetHistory.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("snapshot", snapshot); out.put("byFleet", byFleet); out.put("byStatus", byStatus);
        out.put("alertsByType", alertsByType); out.put("history", history);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private static <T> List<T> paginate(List<T> list, int page, int limit) {
        int from = Math.min((page - 1) * limit, list.size());
        int to = Math.min(from + limit, list.size());
        return list.subList(from, to);
    }

    private static int pages(int total, int limit) {
        return (int) Math.ceil(total / (double) limit);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
