package model;

import java.util.ArrayList;
import java.util.List;

public class Job {
    private int id;
    private double arrivalTime;
    private double stationEntryTime; // istante di ingresso nella stazione CORRENTE

    private JobClass currentClass;
    private double demand;    // richiesta di servizio per la visita corrente, fissata all'ammissione
    private double finishTag; // tag di completamento virtuale (S_ammissione + demand), assegnato dal server

    // [NUOVO] Strutture per tracciare la cronologia delle visite
    public static class VisitRecord {
        public String serverName;
        public double inTime;
        public double outTime;
        public double responseTime; // OUT - IN
    }

    private List<VisitRecord> visits = new ArrayList<>();
    private String currentServer;
    private double currentInTime;

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

    // [NUOVO] Metodi per registrare ingresso e uscita dai server
    public void enterServer(String serverName, double clock) {
        this.currentServer = serverName;
        this.currentInTime = clock;
    }

    public void leaveServer(double clock) {
        VisitRecord v = new VisitRecord();
        v.serverName = this.currentServer;
        v.inTime = this.currentInTime;
        v.outTime = clock;
        v.responseTime = clock - this.currentInTime;
        this.visits.add(v);
    }

    public List<VisitRecord> getVisits() { return visits; }

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