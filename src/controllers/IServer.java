package controllers;

import model.Job;

public interface IServer {
    void addJob(Job job);

    /** Avanza il tempo di servizio virtuale del server di una quantità di tempo reale 'delta'. */
    void advanceVirtualTime(double delta);

    /** Istante reale assoluto del prossimo completamento su questo server (+Infinity se vuoto). */
    double getNextCompletionTime(double now);

    /** Estrae (e rimuove) il job che ha appena completato il servizio. */
    Job popCompletedJob();

    int size();
    double getCapacity();
}