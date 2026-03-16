package utils;

import model.JobClass;

class Params {
    // Tasso di arrivo lambda (verrà variato tra 0.5 e 1.20)
    double lambda = 0.5;

    // --- TEMPI DI SERVIZIO MEDI (Service Demands in secondi) ---
    // Indici array: 0 = Classe 1, 1 = Classe 2, 2 = Classe 3

    // SCENARIO 1FA (Default)
    double[] service_A_1FA = {0.2, 0.4, 0.1};
    double[] service_B_1FA = {0.8, 0.0, 0.0};
    double[] service_P_1FA = {0.0, 0.4, 0.0};

    // SCENARIO 2FA (Più pesante)
    double[] service_A_2FA = {0.2, 0.4, 0.15};
    double[] service_B_2FA = {0.8, 0.0, 0.0};
    double[] service_P_2FA = {0.0, 0.7, 0.0};

    boolean is2FA_enabled = false; // Flag per decidere quale scenario usare

    // Metodi helper per recuperare il tempo medio corretto in base allo stato
    public double getMeanServiceTime_A(JobClass jobClass) {
        int index = jobClass.ordinal(); // CLASS_1 -> 0, CLASS_2 -> 1, ecc.
        return is2FA_enabled ? service_A_2FA[index] : service_A_1FA[index];
    }

    public double getMeanServiceTime_B(JobClass jobClass) {
        int index = jobClass.ordinal();
        return is2FA_enabled ? service_B_2FA[index] : service_B_1FA[index];
    }

    public double getMeanServiceTime_P(JobClass jobClass) {
        int index = jobClass.ordinal();
        return is2FA_enabled ? service_P_2FA[index] : service_P_1FA[index];
    }
}
