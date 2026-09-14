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

    public EnergyCoordinator() {
        hourlyGeneration = new HashMap<>();
        hourlyDemands = new HashMap<>();
        dailyReport = new ArrayList<>(24);
        
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
        intervalResult.setTotalAllocated(totalAllocated);
        intervalResult.setUnmetDemand(unmetDemand);
        intervalResult.setCurtailedEnergy(availableEnergy); // whatever is left

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
}
