package model;

import java.util.LinkedList;
import java.util.Queue;

class ServerNode {
    long numInNode = 0;           // Numero di job nel nodo (in servizio + in coda)
    double areaNode = 0.0;        // Integrale del numero di job nel tempo (per calcolare N)
    double areaBusy = 0.0;        // Integrale del tempo in cui il server è stato occupato (per calcolare U)
    Queue<Job> queue = new LinkedList<>(); // La coda FCFS
}
