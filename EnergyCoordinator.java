import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

public class EnergyCoordinator {
    // Maps an hour (0-23) to its generation amount
    private HashMap<Integer, Double> hourlyGeneration;
    
    // Maps an hour (0-23) to a list of loads requested for that hour
    private HashMap<Integer, List<ConsumerLoad>> hourlyDemands;
    
    // Stores the results for the day. Index corresponds to the hour.
    private ArrayList<IntervalAllocation> dailyReport;

    // Battery Energy Storage System
    private BatteryStorage battery;

    public EnergyCoordinator() {
        hourlyGeneration = new HashMap<>();
        hourlyDemands = new HashMap<>();
        dailyReport = new ArrayList<>(24);
        battery = new BatteryStorage(); // 200 kWh default community battery
        
        // Initialize the report with nulls so we can set specific hours later
        for (int i = 0; i < 24; i++) {
            dailyReport.add(null);
            hourlyDemands.put(i, new ArrayList<>());
        }
    }

    /**
     * Sets the available generation for a specific hour.
     */
    public void setGeneration(int hour, double generation) throws InvalidInputException {
        if (hour < 0 || hour > 23) {
            throw new InvalidInputException("Hour must be between 0 and 23.");
        }
        if (generation < 0) {
            throw new InvalidInputException("Generation cannot be negative.");
        }
        hourlyGeneration.put(hour, generation);
    }

    /**
     * Adds a consumer load request to a specific hour.
     */
    public void addLoadRequest(int hour, ConsumerLoad load) throws InvalidInputException {
        if (hour < 0 || hour > 23) {
            throw new InvalidInputException("Hour must be between 0 and 23.");
        }
        hourlyDemands.get(hour).add(load);
    }

    /**
     * Simulates the allocation process for a specific hour.
     */
    public IntervalAllocation simulateHour(int hour) throws InvalidInputException {
        if (hour < 0 || hour > 23) {
            throw new InvalidInputException("Hour must be between 0 and 23.");
        }

        double generation = hourlyGeneration.getOrDefault(hour, 0.0);
        List<ConsumerLoad> requests = hourlyDemands.get(hour);
        
        // Use a PriorityQueue to sort requests by priority (highest first)
        PriorityQueue<ConsumerLoad> queue = new PriorityQueue<>(requests);
        
        double availableEnergy = generation;
        double totalAllocated = 0.0;
        double unmetDemand = 0.0;

        // Allocate energy based on priority
        while (!queue.isEmpty()) {
            ConsumerLoad load = queue.poll();
            
            // The load figures out how much it takes based on its type and available energy
            double allocated = load.allocate(availableEnergy);
            
            totalAllocated += allocated;
            availableEnergy -= allocated;
            
            unmetDemand += (load.getDemand() - allocated);
        }

        // Create the interval report
        IntervalAllocation intervalResult = new IntervalAllocation(hour, generation, requests);

        // --- Battery Energy Storage Integration ---
        double batteryDischarged = 0.0;
        double batteryCharged = 0.0;

        // Phase 1: If loads went unsatisfied, try discharging battery to cover deficit
        if (unmetDemand > 0 && battery.getStateOfCharge() > 0) {
            batteryDischarged = battery.discharge(unmetDemand);
            if (batteryDischarged > 0) {
                double batteryEnergyAvail = batteryDischarged;
                // Distribute discharged battery energy to unmet loads in priority order
                PriorityQueue<ConsumerLoad> unmetQueue = new PriorityQueue<>(requests);
                while (!unmetQueue.isEmpty() && batteryEnergyAvail > 0.001) {
                    ConsumerLoad load = unmetQueue.poll();
                    double loadDeficit = load.getDemand() - load.getAllocatedEnergy();
                    if (loadDeficit > 0) {
                        double extra = Math.min(loadDeficit, batteryEnergyAvail);
                        load.setAllocatedEnergy(load.getAllocatedEnergy() + extra);
                        batteryEnergyAvail -= extra;
                    }
                }
                // Recalculate totals
                totalAllocated = 0.0;
                unmetDemand = 0.0;
                for (ConsumerLoad load : requests) {
                    totalAllocated += load.getAllocatedEnergy();
                    unmetDemand += (load.getDemand() - load.getAllocatedEnergy());
                }
            }
        }

        // Phase 2: If generation surplus remains, charge battery for later use
        if (availableEnergy > 0) {
            batteryCharged = battery.charge(availableEnergy);
            availableEnergy -= batteryCharged; // Only truly curtailed after battery absorbs
        }

        intervalResult.setTotalAllocated(totalAllocated);
        intervalResult.setUnmetDemand(unmetDemand);
        intervalResult.setCurtailedEnergy(availableEnergy);
        intervalResult.setBatteryCharged(batteryCharged);
        intervalResult.setBatteryDischarged(batteryDischarged);
        intervalResult.setBatterySoC(battery.getStateOfCharge());

        // Lock it in the daily report
        dailyReport.set(hour, intervalResult);

        return intervalResult;
    }

    /**
     * Returns the full day report.
     */
    public ArrayList<IntervalAllocation> getDailyReport() {
        return dailyReport;
    }

    public double getGeneration(int hour) {
        return hourlyGeneration.getOrDefault(hour, 0.0);
    }

    public List<ConsumerLoad> getLoads(int hour) {
        return hourlyDemands.getOrDefault(hour, new ArrayList<>());
    }

    public void clearHourLoads(int hour) {
        if (hourlyDemands.containsKey(hour)) {
            hourlyDemands.get(hour).clear();
        }
    }

    public void reset() {
        hourlyGeneration.clear();
        hourlyDemands.clear();
        dailyReport.clear();
        for (int i = 0; i < 24; i++) {
            dailyReport.add(null);
            hourlyDemands.put(i, new ArrayList<>());
        }
        battery.reset();
    }

    public BatteryStorage getBattery() {
        return battery;
    }
}
