package model;

class Event implements Comparable<Event> {
    public double getEventTime() {
        return eventTime;
    }

    public void setEventTime(double eventTime) {
        this.eventTime = eventTime;
    }

    double eventTime;
    EventType type;
    Job job;

    public Event(double eventTime, EventType type, Job job) {
        this.eventTime = eventTime;
        this.type = type;
        this.job = job;
    }

    // Fondamentale per la PriorityQueue: ordina gli eventi dal più imminente al più lontano
    @Override
    public int compareTo(Event other) {
        return Double.compare(this.eventTime, other.eventTime);
    }
}
