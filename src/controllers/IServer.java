package controllers;

import model.Job;

public interface IServer {
    void computeJobsAdvancement(double startTs, double endTs, Double completedJobResponseTime) throws Exception;
    void addJob(Job job);
    boolean activeJobExists();
    int size();
    double getMinRemainingLife();
    Job getMinRemainingLifeJob();
    double getCapacity();
    Job popCompletedJob(); // Estrae il job che ha finito
}
