import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class WebServer {
    private static final int PORT = 8080;
    private static EnergyCoordinator coordinator = new EnergyCoordinator();
    private static DatabaseManager db = DatabaseManager.getInstance();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API Contexts
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/simulation", new SimulationHandler());
        server.createContext("/api/set-generation", new SetGenerationHandler());
        server.createContext("/api/add-load", new AddLoadHandler());
        server.createContext("/api/simulate-hour", new SimulateHourHandler());
        server.createContext("/api/simulate-all", new SimulateAllHandler());
        server.createContext("/api/load-preset", new LoadPresetHandler());
        server.createContext("/api/reset", new ResetHandler());
        server.createContext("/api/db-records", new DbRecordsHandler());
        server.createContext("/api/reconnect-mysql", new ReconnectMysqlHandler());
        server.createContext("/api/set-battery", new SetBatteryHandler());

        // Static Web Files Context
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // default executor
        server.start();

        System.out.println("=======================================================");
        System.out.println("  Village Energy Coordinator Web Server Running!       ");
        System.out.println("  Open your browser at: http://localhost:" + PORT + "     ");
        System.out.println("  Database Type: " + db.getDbType());
        System.out.println("  Status: " + db.getDbStatusMessage());
        System.out.println("=======================================================");
    }

    // --- Static File Handler ---
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File file = new File("web" + path);
            if (!file.exists() || file.isDirectory()) {
                sendResponse(exchange, 404, "text/plain", "404 Not Found: " + path);
                return;
            }

            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
            else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".json")) contentType = "application/json; charset=UTF-8";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";
            else if (path.endsWith(".png")) contentType = "image/png";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, file.length());

            try (OutputStream os = exchange.getResponseBody(); InputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    // --- API Handlers ---
    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            Map<String, Object> info = db.getDatabaseInfo();
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"dbType\":").append(escapeJson(String.valueOf(info.get("dbType")))).append(",");
            sb.append("\"statusMessage\":").append(escapeJson(String.valueOf(info.get("statusMessage")))).append(",");
            sb.append("\"isConnected\":").append(info.get("isConnected")).append(",");
            sb.append("\"generationRows\":").append(info.get("generationRows")).append(",");
            sb.append("\"loadRows\":").append(info.get("loadRows")).append(",");
            sb.append("\"resultRows\":").append(info.get("resultRows"));
            sb.append("}");

            sendResponse(exchange, 200, "application/json", sb.toString());
        }
    }

    static class SimulationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            String json = buildSimulationJson();
            sendResponse(exchange, 200, "application/json", json);
        }
    }

    static class SetGenerationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            String body = readBody(exchange);
            try {
                int hour = parseJsonInt(body, "hour");
                double generation = parseJsonDouble(body, "generation");

                coordinator.setGeneration(hour, generation);
                db.saveGeneration(hour, generation);

                sendResponse(exchange, 200, "application/json", "{\"success\":true,\"hour\":" + hour + ",\"generation\":" + generation + "}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "application/json", "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class AddLoadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            String body = readBody(exchange);
            try {
                int hour = parseJsonInt(body, "hour");
                String name = parseJsonString(body, "name");
                String type = parseJsonString(body, "type"); // "DIVISIBLE" or "MINIMUM_SERVICE"
                String priorityStr = parseJsonString(body, "priority"); // "CRITICAL", "ESSENTIAL", "DEFERRABLE"
                double demand = parseJsonDouble(body, "demand");
                double minRequired = 0.0;

                Priority priority = Priority.valueOf(priorityStr.toUpperCase());
                ConsumerLoad load;
                if ("MINIMUM_SERVICE".equalsIgnoreCase(type)) {
                    minRequired = parseJsonDouble(body, "minRequired");
                    load = new MinimumServiceLoad(name, priority, demand, minRequired);
                } else {
                    load = new DivisibleLoad(name, priority, demand);
                }

                coordinator.addLoadRequest(hour, load);
                db.saveLoad(hour, load, type, minRequired);

                sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Load added successfully\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "application/json", "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class SimulateHourHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            String body = readBody(exchange);
            try {
                int hour = parseJsonInt(body, "hour");
                IntervalAllocation result = coordinator.simulateHour(hour);
                db.saveSimulationResult(result);

                sendResponse(exchange, 200, "application/json", "{\"success\":true,\"hour\":" + hour + ",\"allocated\":" + result.getTotalAllocated() + ",\"unmet\":" + result.getUnmetDemand() + ",\"curtailed\":" + result.getCurtailedEnergy() + ",\"batteryCharged\":" + result.getBatteryCharged() + ",\"batteryDischarged\":" + result.getBatteryDischarged() + ",\"batterySoC\":" + result.getBatterySoC() + "}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "application/json", "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class SetBatteryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            String body = readBody(exchange);
            try {
                double capacity = parseJsonDouble(body, "capacity");
                double initialSoC = parseJsonDouble(body, "initialSoC");
                if (capacity < 0) {
                    sendResponse(exchange, 400, "application/json", "{\"success\":false,\"error\":\"Capacity cannot be negative\"}");
                    return;
                }
                coordinator.getBattery().setCapacity(capacity);
                coordinator.getBattery().setStateOfCharge(Math.min(initialSoC, capacity));

                sendResponse(exchange, 200, "application/json",
                    "{\"success\":true,\"capacity\":" + coordinator.getBattery().getCapacity()
                    + ",\"stateOfCharge\":" + coordinator.getBattery().getStateOfCharge()
                    + ",\"percentage\":" + coordinator.getBattery().getSoCPercentage() + "}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "application/json", "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class SimulateAllHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            try {
                for (int h = 0; h < 24; h++) {
                    IntervalAllocation result = coordinator.simulateHour(h);
                    db.saveSimulationResult(result);
                }
                sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"All 24 hours simulated and saved to database.\"}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class LoadPresetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            String body = readBody(exchange);
            String preset = parseJsonString(body, "preset");
            if (preset == null || preset.isEmpty()) preset = "standard";

            try {
                coordinator.reset();
                db.clearDatabase();

                applyPresetScenario(preset);

                // Run simulation for all 24 hours and save
                for (int h = 0; h < 24; h++) {
                    IntervalAllocation result = coordinator.simulateHour(h);
                    db.saveSimulationResult(result);
                }

                sendResponse(exchange, 200, "application/json", "{\"success\":true,\"preset\":\"" + preset + "\",\"message\":\"Preset scenario loaded & simulated successfully.\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "application/json", "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            try {
                coordinator.reset();
                db.clearDatabase();
                sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Simulation reset and database cleared.\"}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class DbRecordsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            String table = "simulation_results";
            if (query != null && query.contains("table=")) {
                String[] parts = query.split("table=");
                if (parts.length > 1) {
                    table = parts[1].split("&")[0];
                }
            }

            List<Map<String, Object>> records = db.getTableRecords(table);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"table\":\"").append(table).append("\",\"records\":[");
            for (int i = 0; i < records.size(); i++) {
                if (i > 0) sb.append(",");
                Map<String, Object> r = records.get(i);
                sb.append("{");
                int colIdx = 0;
                for (Map.Entry<String, Object> entry : r.entrySet()) {
                    if (colIdx > 0) sb.append(",");
                    sb.append("\"").append(entry.getKey()).append("\":");
                    Object v = entry.getValue();
                    if (v == null) sb.append("null");
                    else if (v instanceof Number || v instanceof Boolean) sb.append(v);
                    else sb.append(escapeJson(String.valueOf(v)));
                    colIdx++;
                }
                sb.append("}");
            }
            sb.append("]}");

            sendResponse(exchange, 200, "application/json", sb.toString());
        }
    }

    static class ReconnectMysqlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            boolean connected = db.tryConnectMySQL();
            if (!connected) {
                db.tryConnectSQLite();
            }
            Map<String, Object> info = db.getDatabaseInfo();
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"success\":").append(connected).append(",");
            sb.append("\"dbType\":").append(escapeJson(String.valueOf(info.get("dbType")))).append(",");
            sb.append("\"statusMessage\":").append(escapeJson(String.valueOf(info.get("statusMessage"))));
            sb.append("}");

            sendResponse(exchange, 200, "application/json", sb.toString());
        }
    }

    // --- Scenario Presets ---
    private static void applyPresetScenario(String preset) throws InvalidInputException {
        // Generation profile: Solar bell curve peak around 12-14 + constant wind
        double[] solarCurve = {
            0, 0, 0, 0, 0, 5, 18, 38, 65, 88, 110, 125, 130, 120, 105, 80, 50, 22, 5, 0, 0, 0, 0, 0
        };
        double[] windCurve = {
            15, 16, 18, 17, 15, 14, 12, 10, 12, 14, 15, 16, 17, 15, 14, 16, 18, 20, 22, 21, 20, 18, 16, 15
        };

        if ("monsoon".equalsIgnoreCase(preset)) {
            // High wind, heavy cloud cover cutting solar by 70%
            for (int i = 0; i < 24; i++) {
                solarCurve[i] *= 0.3;
                windCurve[i] *= 1.4;
            }
        } else if ("high_demand".equalsIgnoreCase(preset)) {
            // Normal generation, but huge heatwave loads
        }

        for (int h = 0; h < 24; h++) {
            double gen = Math.round((solarCurve[h] + windCurve[h]) * 10.0) / 10.0;
            coordinator.setGeneration(h, gen);
            db.saveGeneration(h, gen);

            // 1. Critical 24/7 Primary Health Clinic
            MinimumServiceLoad clinic = new MinimumServiceLoad("Primary Health Clinic", Priority.CRITICAL, 15.0, 10.0);
            coordinator.addLoadRequest(h, clinic);
            db.saveLoad(h, clinic, "MINIMUM_SERVICE", 10.0);

            // 2. Agricultural Water Pump (Morning 8-11, Afternoon 14-17)
            if ((h >= 8 && h <= 11) || (h >= 14 && h <= 17)) {
                MinimumServiceLoad pump = new MinimumServiceLoad("Community Water Pump", Priority.ESSENTIAL, 35.0, 25.0);
                coordinator.addLoadRequest(h, pump);
                db.saveLoad(h, pump, "MINIMUM_SERVICE", 25.0);
            }

            // 3. Village School & Computer Lab (9am to 4pm)
            if (h >= 9 && h <= 16) {
                DivisibleLoad school = new DivisibleLoad("Village School & Lab", Priority.ESSENTIAL, 28.0);
                coordinator.addLoadRequest(h, school);
                db.saveLoad(h, school, "DIVISIBLE", 0.0);
            }

            // 4. Household Lighting & Appliances (Mornings 6-8, Evenings 18-23)
            if ((h >= 6 && h <= 8) || (h >= 18 && h <= 23)) {
                double houseDemand = (h >= 19 && h <= 22) ? 60.0 : 35.0;
                if ("high_demand".equalsIgnoreCase(preset)) houseDemand *= 1.5;
                DivisibleLoad houses = new DivisibleLoad("Household Domestic Grid", Priority.DEFERRABLE, houseDemand);
                coordinator.addLoadRequest(h, houses);
                db.saveLoad(h, houses, "DIVISIBLE", 0.0);
            }

            // 5. Street Lights (Night 19 to 5)
            if (h >= 19 || h <= 5) {
                DivisibleLoad lights = new DivisibleLoad("Solar Street Lights", Priority.ESSENTIAL, 12.0);
                coordinator.addLoadRequest(h, lights);
                db.saveLoad(h, lights, "DIVISIBLE", 0.0);
            }

            // 6. Agro-Grain Mill (Deferrable mid-day 11 to 15)
            if (h >= 11 && h <= 15) {
                DivisibleLoad mill = new DivisibleLoad("Cooperative Grain Mill", Priority.DEFERRABLE, 45.0);
                coordinator.addLoadRequest(h, mill);
                db.saveLoad(h, mill, "DIVISIBLE", 0.0);
            }
        }
    }

    // --- JSON Serialization Helpers ---
    private static String buildSimulationJson() {
        ArrayList<IntervalAllocation> report = coordinator.getDailyReport();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"hours\":[");

        double totalGen = 0;
        double totalAlloc = 0;
        double totalUnmet = 0;
        double totalCurt = 0;
        int simulatedCount = 0;

        for (int h = 0; h < 24; h++) {
            if (h > 0) sb.append(",");
            sb.append("{");
            sb.append("\"hour\":").append(h).append(",");
            double gen = coordinator.getGeneration(h);
            sb.append("\"generation\":").append(gen).append(",");

            // Loads
            List<ConsumerLoad> loads = coordinator.getLoads(h);
            sb.append("\"loads\":[");
            double hourDemand = 0;
            for (int l = 0; l < loads.size(); l++) {
                if (l > 0) sb.append(",");
                ConsumerLoad cl = loads.get(l);
                hourDemand += cl.getDemand();
                sb.append("{");
                sb.append("\"name\":").append(escapeJson(cl.getName())).append(",");
                sb.append("\"priority\":").append(escapeJson(cl.getPriority().name())).append(",");
                sb.append("\"priorityLevel\":").append(cl.getPriority().getLevel()).append(",");
                sb.append("\"demand\":").append(cl.getDemand()).append(",");
                sb.append("\"allocated\":").append(cl.getAllocatedEnergy()).append(",");
                boolean isMin = cl instanceof MinimumServiceLoad;
                sb.append("\"type\":").append(escapeJson(isMin ? "MINIMUM_SERVICE" : "DIVISIBLE")).append(",");
                sb.append("\"minRequired\":").append(isMin ? ((MinimumServiceLoad) cl).getMinimumRequired() : 0.0);
                sb.append("}");
            }
            sb.append("],");
            sb.append("\"totalDemand\":").append(hourDemand).append(",");

            // Result
            IntervalAllocation res = (report != null && report.size() > h) ? report.get(h) : null;
            if (res != null) {
                simulatedCount++;
                totalGen += res.getTotalGeneration();
                totalAlloc += res.getTotalAllocated();
                totalUnmet += res.getUnmetDemand();
                totalCurt += res.getCurtailedEnergy();

                sb.append("\"simulated\":true,");
                sb.append("\"allocated\":").append(res.getTotalAllocated()).append(",");
                sb.append("\"unmet\":").append(res.getUnmetDemand()).append(",");
                sb.append("\"curtailed\":").append(res.getCurtailedEnergy()).append(",");
                sb.append("\"batteryCharged\":").append(res.getBatteryCharged()).append(",");
                sb.append("\"batteryDischarged\":").append(res.getBatteryDischarged()).append(",");
                sb.append("\"batterySoC\":").append(res.getBatterySoC());
            } else {
                sb.append("\"simulated\":false,");
                sb.append("\"allocated\":0,");
                sb.append("\"unmet\":0,");
                sb.append("\"curtailed\":0,");
                sb.append("\"batteryCharged\":0,");
                sb.append("\"batteryDischarged\":0,");
                sb.append("\"batterySoC\":0");
            }

            sb.append("}");
        }
        sb.append("],");

        // Summary
        sb.append("\"summary\":{");
        sb.append("\"simulatedHours\":").append(simulatedCount).append(",");
        sb.append("\"totalGeneration\":").append(Math.round(totalGen * 100.0) / 100.0).append(",");
        sb.append("\"totalAllocated\":").append(Math.round(totalAlloc * 100.0) / 100.0).append(",");
        sb.append("\"totalUnmet\":").append(Math.round(totalUnmet * 100.0) / 100.0).append(",");
        sb.append("\"totalCurtailed\":").append(Math.round(totalCurt * 100.0) / 100.0).append(",");
        sb.append("\"batteryCapacity\":").append(coordinator.getBattery().getCapacity()).append(",");
        sb.append("\"batterySoC\":").append(Math.round(coordinator.getBattery().getStateOfCharge() * 10.0) / 10.0).append(",");
        sb.append("\"batteryPercent\":").append(Math.round(coordinator.getBattery().getSoCPercentage() * 10.0) / 10.0).append(",");
        sb.append("\"totalBatteryCharged\":").append(Math.round(coordinator.getBattery().getTotalCharged() * 100.0) / 100.0).append(",");
        sb.append("\"totalBatteryDischarged\":").append(Math.round(coordinator.getBattery().getTotalDischarged() * 100.0) / 100.0).append(",");
        sb.append("\"chargeEff\":").append(coordinator.getBattery().getChargeEfficiency()).append(",");
        sb.append("\"dischargeEff\":").append(coordinator.getBattery().getDischargeEfficiency()).append(",");
        double req = totalAlloc + totalUnmet;
        double satisfactionRate = (req > 0) ? (totalAlloc / req) * 100.0 : 100.0;
        sb.append("\"satisfactionRate\":").append(Math.round(satisfactionRate * 10.0) / 10.0);
        sb.append("}");
        sb.append("}");

        return sb.toString();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Mini parser for JSON values
    private static int parseJsonInt(String json, String key) {
        String pat = "\"" + key + "\"\\s*:\\s*(\\d+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pat).matcher(json);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 0;
    }

    private static double parseJsonDouble(String json, String key) {
        String pat = "\"" + key + "\"\\s*:\\s*([-+]?\\d*\\.?\\d+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pat).matcher(json);
        if (m.find()) return Double.parseDouble(m.group(1));
        return 0.0;
    }

    private static String parseJsonString(String json, String key) {
        String pat = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pat).matcher(json);
        if (m.find()) return m.group(1);
        return "";
    }

    private static String escapeJson(String s) {
        if (s == null) return "null";
        return "\"" + escape(s) + "\"";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
