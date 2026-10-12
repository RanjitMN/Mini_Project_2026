/**
 * Simulates a Battery Energy Storage System (BESS) for the village microgrid.
 *
 * Physics:
 * - The battery stores excess solar/wind energy generated during the day
 *   and releases it during evening/night hours when generation is low.
 * - Charge/discharge efficiencies model real-world energy losses (heat, conversion).
 * - Rate limits model physical constraints of battery inverters.
 *
 * Default specs model a community-scale Lithium Iron Phosphate (LiFePO4) bank.
 */
public class BatteryStorage {
    private double capacity;              // Total capacity in kWh
    private double stateOfCharge;         // Current stored energy in kWh
    private double chargeEfficiency;      // 0 to 1 (e.g., 0.92 = 92%)
    private double dischargeEfficiency;   // 0 to 1 (e.g., 0.95 = 95%)
    private double maxChargeRate;         // Max kWh absorbable per hour
    private double maxDischargeRate;      // Max kWh deliverable per hour

    // Tracking for reporting
    private double totalCharged = 0;
    private double totalDischarged = 0;

    /**
     * Default constructor: 200 kWh community battery, starting at 20% SoC.
     */
    public BatteryStorage() {
        this(200.0, 40.0, 0.92, 0.95, 60.0, 60.0);
    }

    public BatteryStorage(double capacity, double initialSoC,
                          double chargeEff, double dischargeEff,
                          double maxChargeRate, double maxDischargeRate) {
        this.capacity = Math.max(0, capacity);
        this.stateOfCharge = Math.max(0, Math.min(initialSoC, this.capacity));
        this.chargeEfficiency = chargeEff;
        this.dischargeEfficiency = dischargeEff;
        this.maxChargeRate = maxChargeRate;
        this.maxDischargeRate = maxDischargeRate;
    }

    /**
     * Attempts to charge the battery with surplus energy.
     * @param surplusEnergy  kWh of excess generation available
     * @return kWh actually absorbed FROM the grid (before efficiency loss)
     */
    public double charge(double surplusEnergy) {
        if (surplusEnergy <= 0 || stateOfCharge >= capacity) return 0;

        // Limit by max charge rate
        double gridAbsorb = Math.min(surplusEnergy, maxChargeRate);

        // After efficiency loss, this much actually enters the battery
        double storable = gridAbsorb * chargeEfficiency;

        // Don't overfill
        double roomLeft = capacity - stateOfCharge;
        if (storable > roomLeft) {
            storable = roomLeft;
            gridAbsorb = storable / chargeEfficiency; // adjust grid-side
        }

        stateOfCharge += storable;
        totalCharged += gridAbsorb;
        return gridAbsorb;
    }

    /**
     * Attempts to discharge the battery to cover unmet demand.
     * @param demandShortfall  kWh of unmet demand that needs covering
     * @return kWh actually delivered TO the loads (after efficiency loss)
     */
    public double discharge(double demandShortfall) {
        if (demandShortfall <= 0 || stateOfCharge <= 0) return 0;

        // Limit by max discharge rate
        double desiredDelivery = Math.min(demandShortfall, maxDischargeRate);

        // To deliver 'desiredDelivery' kWh, we need to pull more from battery (efficiency)
        double pullFromBattery = desiredDelivery / dischargeEfficiency;

        // Don't overdrain
        if (pullFromBattery > stateOfCharge) {
            pullFromBattery = stateOfCharge;
            desiredDelivery = pullFromBattery * dischargeEfficiency;
        }

        stateOfCharge -= pullFromBattery;
        totalDischarged += desiredDelivery;
        return desiredDelivery;
    }

    public void reset() {
        this.stateOfCharge = capacity * 0.2; // Reset to 20%
        this.totalCharged = 0;
        this.totalDischarged = 0;
    }

    // --- Getters ---
    public double getCapacity() { return capacity; }
    public double getStateOfCharge() { return stateOfCharge; }
    public double getSoCPercentage() {
        return capacity == 0 ? 0 : (stateOfCharge / capacity) * 100.0;
    }
    public double getChargeEfficiency() { return chargeEfficiency; }
    public double getDischargeEfficiency() { return dischargeEfficiency; }
    public double getMaxChargeRate() { return maxChargeRate; }
    public double getMaxDischargeRate() { return maxDischargeRate; }
    public double getTotalCharged() { return totalCharged; }
    public double getTotalDischarged() { return totalDischarged; }

    public void setCapacity(double capacity) {
        this.capacity = Math.max(0, capacity);
        this.stateOfCharge = Math.min(stateOfCharge, this.capacity);
    }

    public void setStateOfCharge(double soc) {
        this.stateOfCharge = Math.max(0, Math.min(soc, capacity));
    }

    @Override
    public String toString() {
        return String.format("Battery [%.1f/%.1f kWh (%.0f%%), η_charge=%.0f%%, η_discharge=%.0f%%]",
                stateOfCharge, capacity, getSoCPercentage(), chargeEfficiency * 100, dischargeEfficiency * 100);
    }
}
