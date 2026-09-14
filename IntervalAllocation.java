import java.util.List;
import java.util.ArrayList;

public class IntervalAllocation {
    private int hour;
    private double totalGeneration;
    private double totalAllocated;
    private double unmetDemand;
    private double curtailedEnergy;
    
    // Store a copy of the loads and their allocation states for reporting
    private List<ConsumerLoad> loadsSnapshot;

    public IntervalAllocation(int hour, double totalGeneration, List<ConsumerLoad> loads) {
        this.hour = hour;
        this.totalGeneration = totalGeneration;
        this.loadsSnapshot = new ArrayList<>(loads);
    }

    public int getHour() {
        return hour;
    }

    public double getTotalGeneration() {
        return totalGeneration;
    }

    public double getTotalAllocated() {
        return totalAllocated;
    }

    public void setTotalAllocated(double totalAllocated) {
        this.totalAllocated = totalAllocated;
    }

    public double getUnmetDemand() {
        return unmetDemand;
    }

    public void setUnmetDemand(double unmetDemand) {
        this.unmetDemand = unmetDemand;
    }

    public double getCurtailedEnergy() {
        return curtailedEnergy;
    }

    public void setCurtailedEnergy(double curtailedEnergy) {
        this.curtailedEnergy = curtailedEnergy;
    }

    public List<ConsumerLoad> getLoadsSnapshot() {
        return loadsSnapshot;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Hour %02d: Generation: %.2f kWh | Allocated: %.2f kWh | Unmet: %.2f kWh | Curtailed: %.2f kWh%n",
                hour, totalGeneration, totalAllocated, unmetDemand, curtailedEnergy));
        
        for (ConsumerLoad load : loadsSnapshot) {
            sb.append(String.format("  - %-25s : Demanded %.2f kWh, Allocated %.2f kWh%n",
                    load.getName(), load.getDemand(), load.getAllocatedEnergy()));
        }
        return sb.toString();
    }
}
