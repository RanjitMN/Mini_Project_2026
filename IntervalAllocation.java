import java.util.List;
import java.util.ArrayList;

public class IntervalAllocation {
    private int hour;
    private double totalGeneration;
    private double totalAllocated;
    private double unmetDemand;
    private double curtailedEnergy;

    // Battery Energy Storage System (BESS) tracking
    private double batteryCharged;
    private double batteryDischarged;
    private double batterySoC;  // State of Charge after this hour (kWh)
    
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

    // --- Battery getters and setters ---
    public double getBatteryCharged() { return batteryCharged; }
    public void setBatteryCharged(double batteryCharged) { this.batteryCharged = batteryCharged; }

    public double getBatteryDischarged() { return batteryDischarged; }
    public void setBatteryDischarged(double batteryDischarged) { this.batteryDischarged = batteryDischarged; }

    public double getBatterySoC() { return batterySoC; }
    public void setBatterySoC(double batterySoC) { this.batterySoC = batterySoC; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Hour %02d: Generation: %.2f kWh | Allocated: %.2f kWh | Unmet: %.2f kWh | Curtailed: %.2f kWh",
                hour, totalGeneration, totalAllocated, unmetDemand, curtailedEnergy));
        if (batteryCharged > 0 || batteryDischarged > 0) {
            sb.append(String.format(" | Battery: +%.2f/-%.2f kWh (SoC: %.1f kWh)",
                    batteryCharged, batteryDischarged, batterySoC));
        }
        sb.append(String.format("%n"));
        
        for (ConsumerLoad load : loadsSnapshot) {
            sb.append(String.format("  - %-25s : Demanded %.2f kWh, Allocated %.2f kWh%n",
                    load.getName(), load.getDemand(), load.getAllocatedEnergy()));
        }
        return sb.toString();
    }
}
