public abstract class ConsumerLoad implements Comparable<ConsumerLoad> {
    private String name;
    private Priority priority;
    private double demand;
    
    // Tracks how much energy was actually allocated to this load
    private double allocatedEnergy;

    public ConsumerLoad(String name, Priority priority, double demand) throws InvalidInputException {
        if (demand < 0) {
            throw new InvalidInputException("Demand cannot be negative for load: " + name);
        }
        this.name = name;
        this.priority = priority;
        this.demand = demand;
        this.allocatedEnergy = 0.0;
    }

    public String getName() {
        return name;
    }

    public Priority getPriority() {
        return priority;
    }

    public double getDemand() {
        return demand;
    }

    public double getAllocatedEnergy() {
        return allocatedEnergy;
    }

    protected void setAllocatedEnergy(double allocatedEnergy) {
        this.allocatedEnergy = allocatedEnergy;
    }

    /**
     * Allocates available energy to this load.
     * @param availableEnergy The amount of energy available to allocate.
     * @return The amount of energy consumed by this load.
     */
    public abstract double allocate(double availableEnergy);

    @Override
    public int compareTo(ConsumerLoad other) {
        // Sort descending by priority level
        int priorityComparison = Integer.compare(other.priority.getLevel(), this.priority.getLevel());
        
        // If priorities are equal, we could sort by demand (e.g., smaller demand first)
        // or just consider them equal in priority. We'll leave it at priority for now.
        return priorityComparison;
    }
    
    @Override
    public String toString() {
        return String.format("%s (Priority: %s, Demand: %.2f kWh)", name, priority, demand);
    }
}
