public class DivisibleLoad extends ConsumerLoad {

    public DivisibleLoad(String name, Priority priority, double demand) throws InvalidInputException {
        super(name, priority, demand);
    }

    @Override
    public double allocate(double availableEnergy) {
        // A divisible load takes as much as it can up to its demand
        double allocated = Math.min(getDemand(), availableEnergy);
        setAllocatedEnergy(allocated);
        return allocated;
    }
}
