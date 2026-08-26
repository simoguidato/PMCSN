package model;

public class Job {
    private int id;
    private double arrivalTime;
    private double stationEntryTime; // istante di ingresso nella stazione CORRENTE

    private JobClass currentClass;
    private double demand;    // richiesta di servizio per la visita corrente, fissata all'ammissione
    private double finishTag; // tag di completamento virtuale (S_ammissione + demand), assegnato dal server

    public Job(int id, double arrivalTime, JobClass currentClass, double initialDemand) {
        this.id = id;
        this.arrivalTime = arrivalTime;
        this.stationEntryTime = arrivalTime;
        this.currentClass = currentClass;
        this.demand = initialDemand;
    }

    public Job(double arrivalTime, JobClass currentClass, double initialDemand) {
        this.arrivalTime = arrivalTime;
        this.stationEntryTime = arrivalTime;
        this.currentClass = currentClass;
        this.demand = initialDemand;
    }

    public int getId() { return id; }
    public double getArrivalTime() { return arrivalTime; }

    public double getStationEntryTime() { return stationEntryTime; }
    public void setStationEntryTime(double stationEntryTime) { this.stationEntryTime = stationEntryTime; }

    public JobClass getCurrentClass() { return currentClass; }
    public void setCurrentClass(JobClass currentClass) { this.currentClass = currentClass; }

    public double getDemand() { return demand; }
    public void setDemand(double demand) { this.demand = demand; }

    public double getFinishTag() { return finishTag; }
    public void setFinishTag(double finishTag) { this.finishTag = finishTag; }
}