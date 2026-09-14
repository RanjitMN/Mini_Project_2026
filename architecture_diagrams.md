# Code Architecture Visualizations

To help you and the jury understand how the code is structured, here are two visualizations. You can use these concepts to draw diagrams on a whiteboard or presentation slide.

## 1. Class Structure (Object-Oriented Design)

This diagram shows how the different Java files (classes) relate to each other. 
*   **Solid arrows** mean "inherits from" (e.g., `DivisibleLoad` is a type of `ConsumerLoad`).
*   **Dotted arrows** mean "uses" or "contains" (e.g., the `EnergyCoordinator` uses `ConsumerLoad` objects).

```mermaid
classDiagram
    class Main {
        -EnergyCoordinator coordinator
        -Scanner scanner
        +main(String[] args)
        +handleManualInput()
        +autoFillRemainingHours()
    }

    class EnergyCoordinator {
        -HashMap hourlyGeneration
        -HashMap hourlyDemands
        -ArrayList dailyReport
        +setGeneration(hour, amount)
        +addLoadRequest(hour, ConsumerLoad)
        +simulateHour(hour) : IntervalAllocation
    }

    class IntervalAllocation {
        -int hour
        -double totalGeneration
        -double totalAllocated
        -double unmetDemand
        -double curtailedEnergy
        -List loadsSnapshot
    }

    class ConsumerLoad {
        <<abstract>>
        -String name
        -Priority priority
        -double demand
        -double allocatedEnergy
        +allocate(availableEnergy)* double
        +compareTo(ConsumerLoad) int
    }

    class DivisibleLoad {
        +allocate(availableEnergy) double
    }

    class MinimumServiceLoad {
        -double minimumRequired
        +allocate(availableEnergy) double
    }

    class Priority {
        <<enumeration>>
        CRITICAL
        ESSENTIAL
        DEFERRABLE
    }

    Main ..> EnergyCoordinator : "Controls"
    EnergyCoordinator ..> IntervalAllocation : "Generates Reports"
    EnergyCoordinator ..> ConsumerLoad : "Sorts in PriorityQueue"
    ConsumerLoad <|-- DivisibleLoad : "Extends"
    ConsumerLoad <|-- MinimumServiceLoad : "Extends"
    ConsumerLoad ..> Priority : "Has a"
```

### Key Takeaways for the Jury:
*   The `Main` class only handles the user interface (Console). It knows nothing about how energy is calculated.
*   The `EnergyCoordinator` is the "Brain". It stores data and runs the math.
*   `ConsumerLoad` is **Abstract**. It forces `DivisibleLoad` and `MinimumServiceLoad` to define their own specific rules for how they take energy (Polymorphism).

---

## 2. Execution Flow: How an Hour is Simulated

This flowchart shows what happens under the hood when you hit "Simulate Hour" in the console. This visualizes the logic inside `EnergyCoordinator.simulateHour(int hour)`.

```mermaid
flowchart TD
    Start([Start: simulateHour]) --> GetGen[Get Generation for the Hour]
    GetGen --> GetLoads[Get all ConsumerLoads for the Hour]
    GetLoads --> PutInQueue[Put all Loads into a PriorityQueue]
    
    PutInQueue --> LoopStart{Is Queue Empty?}
    
    LoopStart -- No --> PollQueue[Poll highest priority Load from Queue]
    PollQueue --> CheckType{What type of Load is this?}
    
    CheckType -- DivisibleLoad --> TakeDiv[Take up to Demand or Available Energy]
    CheckType -- MinimumServiceLoad --> CheckMin{Is Available >= Minimum?}
    
    CheckMin -- Yes --> TakeDiv
    CheckMin -- No --> TakeZero[Take 0 Energy. Pass on to next load.]
    
    TakeDiv --> UpdateTotals[Deduct taken energy from Available, add to Allocated]
    TakeZero --> UpdateTotals
    
    UpdateTotals --> LoopStart
    
    LoopStart -- Yes (Queue Empty) --> CreateReport[Create IntervalAllocation object]
    CreateReport --> SaveReport[Save to dailyReport ArrayList]
    SaveReport --> End([End: Return Result to Main])
```

### Key Takeaways for the Jury:
*   The **PriorityQueue** automatically pushes Critical loads to the front of the line, guaranteeing they are evaluated first.
*   The logic branches differently depending on the **Type of Load** being evaluated, demonstrating how the Minimum Service constraint actively prevents wasting energy.
