public class MinimumServiceLoad extends ConsumerLoad {
    private double minimumRequired;

    public MinimumServiceLoad(String name, Priority priority, double demand, double minimumRequired) throws InvalidInputException {
        super(name, priority, demand);
        if (minimumRequired < 0) {
            throw new InvalidInputException("Minimum required energy cannot be negative for load: " + name);
        }
        if (minimumRequired > demand) {
            throw new InvalidInputException("Minimum required energy cannot exceed total demand for load: " + name);
        }
        this.minimumRequired = minimumRequired;
    }

    public double getMinimumRequired() {
        return minimumRequired;
    }

    @Override
    public double allocate(double availableEnergy) {
        // A minimum service load gets NOTHING if available energy is less than its minimum requirement
        if (availableEnergy < minimumRequired) {
            setAllocatedEnergy(0.0);
            return 0.0;
        }

        // Otherwise, it takes as much as it needs up to its demand
        double allocated = Math.min(getDemand(), availableEnergy);
        setAllocatedEnergy(allocated);
        return allocated;
    }
}
