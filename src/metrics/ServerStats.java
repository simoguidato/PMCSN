package metrics;

public class ServerStats {
    private int serverIndex;

    public ServerStats(int index) {
        this.serverIndex = index;
    }

    // Se in futuro vorrai de-commentare la riga in AbstractServer
    // "stats.updateServerStats(...)", potrai implementare la logica qui dentro.
    // Per ora ci basta che la classe esista per non dare errore di compilazione.
}
