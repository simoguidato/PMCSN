import pandas as pd
import numpy as np

# ==========================================
# 1. CONFIGURAZIONE E CARICAMENTO
# ==========================================
file_path = 'trace_visite_job.csv'
bin_size = 800.0  # Dimensione della finestra temporale in secondi

print(f"Caricamento di {file_path} in corso...")
df = pd.read_csv(file_path)
t_max = df['Completion'].max()

# Creazione dei bin (finestre temporali)
bins = np.arange(0, t_max + bin_size, bin_size)
centers = (bins[:-1] + bins[1:]) / 2

print(f"Dati caricati: {len(df)} job. Orizzonte: {t_max:.1f} s. Calcolo metriche in corso...")

# Dizionario per raccogliere tutte le metriche
dati_aggregati = {'Tempo_Mediano_Finestra': centers}

# ==========================================
# 2. THROUGHPUT (X)
# ==========================================
counts, _ = np.histogram(df['Completion'].dropna(), bins=bins)
dati_aggregati['Throughput'] = counts / bin_size

# ==========================================
# 3. TEMPO DI RISPOSTA (E[R])
# ==========================================
df['bin'] = pd.cut(df['Completion'], bins=bins)
dati_aggregati['Tempo_Risposta_Medio'] = df.groupby('bin', observed=False)['TempoRispostaTotal'].mean().values

# ==========================================
# 4. NUMERO DI JOB (E[N])
# ==========================================
arrivals = df['Arrival0'].dropna().values
completions = df['Completion'].dropna().values
events = sorted([(t, 1) for t in arrivals] + [(t, -1) for t in completions])

times = np.array([e[0] for e in events])
deltas = np.array([e[1] for e in events])
n_active = np.cumsum(deltas)

n_mean_binned = np.zeros(len(bins)-1)
for i in range(len(bins)-1):
    t_start, t_end = bins[i], bins[i+1]
    mask = (times >= t_start) & (times < t_end)
    if not np.any(mask):
        n_mean_binned[i] = n_mean_binned[i-1] if i > 0 else 0
        continue

    t_w = times[mask]
    n_w = n_active[mask]
    t_c = np.concatenate([[t_start], t_w, [t_end]])
    n_c = np.concatenate([[n_mean_binned[i-1] if i>0 else 0], n_w, [n_w[-1]]])

    area = np.sum(np.diff(t_c) * n_c[:-1])
    n_mean_binned[i] = area / bin_size

dati_aggregati['Job_Nel_Sistema_N'] = n_mean_binned

# ==========================================
# 5. UTILIZZAZIONE SERVER (A, B, P)
# ==========================================
def calculate_utilization(server_name):
    in_times, out_times = [], []
    for i in range(1, 6):
        if f'Server_{i}' in df.columns:
            mask = df[f'Server_{i}'] == server_name
            in_times.extend(df.loc[mask, f'IN_{i}'].dropna().values)
            out_times.extend(df.loc[mask, f'OUT_{i}'].dropna().values)

    if not in_times:
        return np.zeros(len(bins)-1)

    srv_events = sorted([(t, 1) for t in in_times] + [(t, -1) for t in out_times])
    s_times = np.array([e[0] for e in srv_events])
    s_n_active = np.cumsum([e[1] for e in srv_events])

    util_binned = np.zeros(len(bins)-1)
    for i in range(len(bins)-1):
        t_start, t_end = bins[i], bins[i+1]
        mask = (s_times >= t_start) & (s_times < t_end)

        if not np.any(mask):
            util_binned[i] = 1 if (i > 0 and util_binned[i-1] > 0) else 0
            continue

        t_c = np.concatenate([[t_start], s_times[mask], [t_end]])
        n_c = np.concatenate([[1 if (i>0 and util_binned[i-1]>0) else 0], s_n_active[mask], [s_n_active[mask][-1]]])

        busy_time = np.sum(np.diff(t_c) * (n_c[:-1] > 0).astype(float))
        util_binned[i] = busy_time / bin_size

    return util_binned

for srv in ['A', 'B', 'P']:
    dati_aggregati[f'Utilizzazione_{srv}'] = calculate_utilization(srv)

# ==========================================
# 6. ESPORTAZIONE DATI
# ==========================================
df_export = pd.DataFrame(dati_aggregati)
out_file = 'dataset_analitico_temporale.csv'
df_export.to_csv(out_file, index=False, float_format='%.6f')

print(f"\nEsportazione completata! Creato il file: {out_file}")
print("Anteprima dei dati estratti:")
print(df_export.head())

# Statistiche globali a schermo
print("\n--- STATISTICHE GLOBALI DELLA RUN ---")
print(f"Tempo di Risposta Medio Totale: {df['TempoRispostaTotal'].mean():.4f} s")
print(f"Throughput Medio Globale: {len(df) / t_max:.4f} job/s")