import java.util.Scanner;
import java.util.Random;
import java.util.ArrayList;

public class Main {
    private static EnergyCoordinator coordinator = new EnergyCoordinator();
    private static Scanner scanner = new Scanner(System.in);
    private static int currentHour = 0;

    public static void main(String[] args) {
        System.out.println("============================================");
        System.out.println("  Village Energy Coordinator Simulation     ");
        System.out.println("============================================");

        boolean running = true;

        while (running && currentHour < 24) {
            System.out.println("\n--- Hour " + currentHour + " ---");
            System.out.println("1. Manually enter data for this hour");
            System.out.println("2. Auto-fill and simulate the rest of the day");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine();

            try {
                switch (choice) {
                    case "1":
                        handleManualInput();
                        break;
                    case "2":
                        autoFillRemainingHours();
                        running = false; // Finished the day
                        break;
                    case "3":
                        running = false;
                        System.out.println("Exiting simulation.");
                        break;
                    default:
                        System.out.println("Invalid option. Please try again.");
                }
            } catch (InvalidInputException e) {
                System.out.println("ERROR: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("ERROR: Invalid number format. Please enter valid numbers.");
            }
        }

        if (currentHour == 24 || choiceIsAuto(currentHour)) {
            printDailyReport();
        }
        
        scanner.close();
    }
    
    private static boolean choiceIsAuto(int hour) {
        return hour >= 24; // Simple check, since autoFill increments hour to 24
    }

    private static void handleManualInput() throws InvalidInputException {
        System.out.print("Enter total solar/wind generation (kWh) for Hour " + currentHour + ": ");
        double generation = Double.parseDouble(scanner.nextLine());
        coordinator.setGeneration(currentHour, generation);

        boolean addingLoads = true;
        while (addingLoads) {
            System.out.println("\nAdd a Consumer Load for Hour " + currentHour + "?");
            System.out.println("1. Add Divisible Load (e.g., Household, School)");
            System.out.println("2. Add Minimum Service Load (e.g., Water Pump, Clinic)");
            System.out.println("3. Done adding loads. Simulate hour.");
            System.out.print("Choice: ");
            String loadChoice = scanner.nextLine();

            if (loadChoice.equals("3")) {
                addingLoads = false;
                continue;
            }

            if (!loadChoice.equals("1") && !loadChoice.equals("2")) {
                System.out.println("Invalid choice.");
                continue;
            }

            System.out.print("Enter Load Name: ");
            String name = scanner.nextLine();

            System.out.println("Select Priority:");
            System.out.println("3. CRITICAL");
            System.out.println("2. ESSENTIAL");
            System.out.println("1. DEFERRABLE");
            System.out.print("Choice (1-3): ");
            int priorityLevel = Integer.parseInt(scanner.nextLine());
            Priority priority = getPriorityFromLevel(priorityLevel);

            System.out.print("Enter Demand (kWh): ");
            double demand = Double.parseDouble(scanner.nextLine());

            if (loadChoice.equals("1")) {
                coordinator.addLoadRequest(currentHour, new DivisibleLoad(name, priority, demand));
                System.out.println("Added Divisible Load.");
            } else if (loadChoice.equals("2")) {
                System.out.print("Enter Minimum Required Energy (kWh): ");
                double minRequired = Double.parseDouble(scanner.nextLine());
                coordinator.addLoadRequest(currentHour, new MinimumServiceLoad(name, priority, demand, minRequired));
                System.out.println("Added Minimum Service Load.");
            }
        }

        // Simulate the hour and print the result
        IntervalAllocation result = coordinator.simulateHour(currentHour);
        System.out.println("\n>> RESULT FOR HOUR " + currentHour + " <<");
        System.out.print(result.toString());

        currentHour++;
    }

    private static Priority getPriorityFromLevel(int level) {
        switch (level) {
            case 3: return Priority.CRITICAL;
            case 2: return Priority.ESSENTIAL;
            case 1: return Priority.DEFERRABLE;
            default: return Priority.DEFERRABLE;
        }
    }

    private static void autoFillRemainingHours() throws InvalidInputException {
        Random rand = new Random();
        System.out.println("\nAuto-filling hours " + currentHour + " to 23...");

        for (int h = currentHour; h < 24; h++) {
            // Generate some random generation curve (lower at night, higher midday)
            double baseGen = (h >= 8 && h <= 18) ? 15.0 : 5.0; 
            double generation = baseGen + (rand.nextDouble() * 5); // fluctuate
            coordinator.setGeneration(h, generation);

            // Add standard loads
            coordinator.addLoadRequest(h, new MinimumServiceLoad("Clinic", Priority.CRITICAL, 4.0, 3.0)); // Needs at least 3
            coordinator.addLoadRequest(h, new MinimumServiceLoad("Water Pump", Priority.ESSENTIAL, 5.0, 5.0)); // Must have all 5
            coordinator.addLoadRequest(h, new DivisibleLoad("School", Priority.ESSENTIAL, 3.0));
            coordinator.addLoadRequest(h, new DivisibleLoad("Households", Priority.DEFERRABLE, 8.0));

            // Simulate the hour immediately
            coordinator.simulateHour(h);
        }
        
        System.out.println("Auto-fill complete.");
        currentHour = 24;
    }

    private static void printDailyReport() {
        System.out.println("\n============================================");
        System.out.println("          FULL DAY SUMMARY REPORT           ");
        System.out.println("============================================");
        
        ArrayList<IntervalAllocation> report = coordinator.getDailyReport();
        
        double totalGenDay = 0;
        double totalAllocatedDay = 0;
        double totalUnmetDay = 0;
        double totalCurtailedDay = 0;

        for (int i = 0; i < report.size(); i++) {
            IntervalAllocation allocation = report.get(i);
            if (allocation != null) {
                System.out.printf("Hour %02d | Gen: %5.2f | Alloc: %5.2f | Unmet: %5.2f | Curtailed: %5.2f%n", 
                    allocation.getHour(), 
                    allocation.getTotalGeneration(), 
                    allocation.getTotalAllocated(), 
                    allocation.getUnmetDemand(), 
                    allocation.getCurtailedEnergy());
                    
                totalGenDay += allocation.getTotalGeneration();
                totalAllocatedDay += allocation.getTotalAllocated();
                totalUnmetDay += allocation.getUnmetDemand();
                totalCurtailedDay += allocation.getCurtailedEnergy();
            }
        }
        
        System.out.println("--------------------------------------------");
        System.out.printf("DAY TOTALS | Gen: %.2f | Alloc: %.2f | Unmet: %.2f | Curtailed: %.2f%n",
                totalGenDay, totalAllocatedDay, totalUnmetDay, totalCurtailedDay);
        
        if (totalGenDay > 0) {
            double utilization = (totalAllocatedDay / totalGenDay) * 100;
            System.out.printf("Overall Energy Utilization: %.2f%%%n", utilization);
        }
    }
}
