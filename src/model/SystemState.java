package model;

class SystemState {
    // Numero di job attualmente presenti in ciascun server (in coda + in servizio)
    long jobsInA = 0;
    long jobsInB = 0;
    long jobsInP = 0;

    // Aree integrali per le statistiche (necessarie per calcolare l'utilizzazione e N medio)
    double areaA = 0.0;
    double areaB = 0.0;
    double areaP = 0.0;

    // Contatori globali
    long totalJobsCompleted = 0;
    double totalResponseTime = 0.0;
}
