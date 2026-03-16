package model;

public class Job {
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    int id;

    public double getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(double arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    double arrivalTime; // Tempo in cui entra nel sistema (T_in)

    public JobClass getCurrentClass() {
        return currentClass;
    }

    public void setCurrentClass(JobClass currentClass) {
        this.currentClass = currentClass;
    }

    JobClass currentClass;

    public Job(int id, double arrivalTime, JobClass currentClass) {
        this.id = id;
        this.arrivalTime = arrivalTime;
        this.currentClass = currentClass;
    }
}
