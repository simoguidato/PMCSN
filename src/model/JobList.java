package model;

import java.util.ArrayList;
import java.util.List;

public class JobList {
    private List<Job> jobs;

    public JobList() {
        this.jobs = new ArrayList<>();
    }

    public void add(Job job) {
        this.jobs.add(job);
    }

    public void remove(Job job) {
        this.jobs.remove(job);
    }

    public int size() {
        return this.jobs.size();
    }

    public boolean activeJobExists() {
        return !this.jobs.isEmpty();
    }

    public List<Job> getJobs() {
        return this.jobs;
    }

    // Trova il valore minimo di vita residua tra i job correnti
    public double minRemainingLife() {
        if (jobs.isEmpty()) return Double.POSITIVE_INFINITY;

        double min = Double.POSITIVE_INFINITY;
        for (Job job : jobs) {
            if (job.getRemainingLife() < min) {
                min = job.getRemainingLife();
            }
        }
        return min;
    }

    // Restituisce l'oggetto Job che ha la vita residua minore (quello che finirà per primo)
    public Job getMinRemainingLifeJob() {
        if (jobs.isEmpty()) return null;

        Job minJob = jobs.get(0);
        for (Job job : jobs) {
            if (job.getRemainingLife() < minJob.getRemainingLife()) {
                minJob = job;
            }
        }
        return minJob;
    }
}