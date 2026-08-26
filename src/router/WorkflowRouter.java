package router;

import engine.SystemContext;
import model.Job;
import model.JobClass;
import model.ServerId;

public class WorkflowRouter {
    private SystemContext ctx;

    public WorkflowRouter(SystemContext ctx) {
        this.ctx = ctx;
    }

    public void routeJob(Job job, ServerId currentServer, double currentClock) {
        if (currentServer == ServerId.SERVER_A) {
            if (job.getCurrentClass() == JobClass.CLASS_1) {
                double mean = ctx.params.getMeanServiceTime_B(JobClass.CLASS_1);
                job.setDemand(ctx.rng.getServiceTimeB(mean));
                job.setCurrentClass(JobClass.CLASS_2);
                job.setStationEntryTime(currentClock);

                job.enterServer("B", currentClock); // [NUOVO] Registra ingresso
                ctx.serverB.addJob(job);
            }
            else if (job.getCurrentClass() == JobClass.CLASS_2) {
                double mean = ctx.params.getMeanServiceTime_P(JobClass.CLASS_2);
                job.setDemand(ctx.rng.getServiceTimeP(mean));
                job.setStationEntryTime(currentClock);

                job.enterServer("P", currentClock); // [NUOVO] Registra ingresso
                ctx.serverP.addJob(job);
            }
            else if (job.getCurrentClass() == JobClass.CLASS_3) {
                // Il job esce dal sistema, il record finale viene chiuso dal MetricsCollector
                ctx.metrics.recordJobCompleted(job, currentClock);
            }
        }
        else if (currentServer == ServerId.SERVER_B) {
            double mean = ctx.params.getMeanServiceTime_A(JobClass.CLASS_2);
            job.setDemand(ctx.rng.getServiceTimeA(mean));
            job.setStationEntryTime(currentClock);

            job.enterServer("A", currentClock); // [NUOVO] Registra ingresso (visita 2)
            ctx.serverA.addJob(job);
        }
        else if (currentServer == ServerId.SERVER_P) {
            job.setCurrentClass(JobClass.CLASS_3);
            double mean = ctx.params.getMeanServiceTime_A(JobClass.CLASS_3);
            job.setDemand(ctx.rng.getServiceTimeA(mean));
            job.setStationEntryTime(currentClock);

            job.enterServer("A", currentClock); // [NUOVO] Registra ingresso (visita 3)
            ctx.serverA.addJob(job);
        }
    }
}