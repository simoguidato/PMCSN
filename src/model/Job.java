package model;


public class Job {
    private int id;
    private double arrivalTime;
    private double stationEntryTime; // istante di ingresso nella stazione CORRENTE
    private JobClass currentClass;
    private double remainingLife; // Lavoro rimanente per il PS

    // Costruttore con ID
    public Job(int id, double arrivalTime, JobClass currentClass, double initialDemand) {
        this.id = id;
        this.arrivalTime = arrivalTime;
        this.stationEntryTime = arrivalTime;
        this.currentClass = currentClass;
        this.remainingLife = initialDemand;
    }

    public Job(double arrivalTime, JobClass currentClass, double initialDemand) {
        this.arrivalTime = arrivalTime;
        this.stationEntryTime = arrivalTime;
        this.currentClass = currentClass;
        this.remainingLife = initialDemand;
    }

    public int getId() { return id; }
    public double getArrivalTime() { return arrivalTime; }

    public double getStationEntryTime() { return stationEntryTime; }
    public void setStationEntryTime(double stationEntryTime) { this.stationEntryTime = stationEntryTime; }

    public JobClass getCurrentClass() { return currentClass; }
    public void setCurrentClass(JobClass currentClass) { this.currentClass = currentClass; }

    public double getRemainingLife() { return remainingLife; }
    public void setRemainingLife(double remainingLife) { this.remainingLife = remainingLife; }

    public void decreaseRemainingLife(double amount) throws Exception {
        this.remainingLife -= amount;
        if (this.remainingLife < 0) {
            // Tolleranza per errori di virgola mobile (arrotondamenti)
            if (this.remainingLife > -1e-9) {
                this.remainingLife = 0.0;
            } else {
                throw new Exception("Errore: la vita residua del job è scesa sotto lo zero! (" + this.remainingLife + ")");
            }
        }
    }
}