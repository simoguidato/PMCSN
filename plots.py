#!/usr/bin/env python3
"""
File attesi in csv/:
    validation_scan.csv
    transient_analysis_lambda0.5_trajectories.csv
    transient_analysis_lambda1.2_trajectories.csv
    convergence_lambda0.50.csv
    convergence_lambda1.20.csv
    convergence_visualization_summary.csv
    convergence_visualization_rng_seeds.csv
    response_times_B_HyperExp_acf.csv
    regime_experiment.csv
    exp_vs_hyperexp_comparison.csv
    response_times_B_Exp.csv
    response_times_B_HyperExp.csv
    heavy_load_experiment.csv
    heavy_Exp.csv
    heavy_HyperExp.csv
    trace_visite_job.csv
    job_tracing_metadata.csv

"""

from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


# =============================================================================
# PARAMETRI DEL MODELLO
# =============================================================================

LAMBDA_SCAN_MIN = 0.50
LAMBDA_SCAN_MAX = 1.20
LAMBDA_HEAVY = 1.40
WARMUP_TIME = 100_000.0
WELCH_HALF_WINDOW = 150
CHOSEN_BATCH_SIZE = 8_000
ACF_MAX_LAG = 20_000

DEMANDS = {
    "1FA": {"A": 0.70, "B": 0.80, "P": 0.40},
    "2FA": {"A": 0.75, "B": 0.80, "P": 0.70},
}

DEMANDS_UPGRADED_B = {
    "1FA": {"A": 0.70, "B": 0.40, "P": 0.40},
    "2FA": {"A": 0.75, "B": 0.40, "P": 0.70},
}

VISITS = {"A": 3.0, "B": 1.0, "P": 1.0}


# =============================================================================
# INFRASTRUTTURA
# =============================================================================

@dataclass
class PlotRecord:
    stem: str
    descrizione: str
    sorgenti: str
    priorita: str
    stato: str = "generated"


class MissingInput(RuntimeError):
    pass


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Genera tutti i grafici definitivi del progetto PMCSN."
    )
    p.add_argument("--csv-dir", default="csv", help="Directory dei CSV (default: csv)")
    p.add_argument("--out-dir", default="plots", help="Directory di output (default: plots)")
    p.add_argument("--dpi", type=int, default=300, help="DPI dei PNG (default: 300)")
    return p.parse_args()


def find_file(csv_dir: Path, *names: str) -> Path:
    """Restituisce il primo filename esistente tra le alternative."""
    for name in names:
        p = csv_dir / name
        if p.exists():
            return p
    raise MissingInput("nessuno di questi file esiste: " + ", ".join(names))


def save_figure(fig: plt.Figure, out_dir: Path, stem: str, dpi: int) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_dir / f"{stem}.png", dpi=dpi, bbox_inches="tight")
    fig.savefig(out_dir / f"{stem}.pdf", bbox_inches="tight")
    plt.close(fig)


def finish_axis(ax: plt.Axes, xlabel: str, ylabel: str, title: str | None = None) -> None:
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    if title:
        ax.set_title(title)
    ax.grid(True, alpha=0.25)


def theory_response(lam: np.ndarray | float, demands: dict[str, float]) -> np.ndarray:
    """R = sum_k D_k/(1-lambda D_k), valido solo dove rho_k < 1."""
    x = np.asarray(lam, dtype=float)
    result = np.zeros_like(x, dtype=float)
    stable = np.ones_like(x, dtype=bool)
    for d in demands.values():
        stable &= x * d < 1.0
    result[:] = np.nan
    if np.any(stable):
        rs = np.zeros(np.count_nonzero(stable), dtype=float)
        for d in demands.values():
            rs += d / (1.0 - x[stable] * d)
        result[stable] = rs
    return result


def theory_population(lam: np.ndarray | float, demands: dict[str, float], server: str) -> np.ndarray:
    x = np.asarray(lam, dtype=float)
    d = demands[server]
    rho = x * d
    out = np.full_like(x, np.nan, dtype=float)
    mask = rho < 1.0
    out[mask] = rho[mask] / (1.0 - rho[mask])
    return out


def theory_total_population(lam: np.ndarray | float, demands: dict[str, float]) -> np.ndarray:
    x = np.asarray(lam, dtype=float)
    out = np.zeros_like(x, dtype=float)
    stable = np.ones_like(x, dtype=bool)
    for s in ("A", "B", "P"):
        vals = theory_population(x, demands, s)
        stable &= np.isfinite(vals)
        out += np.nan_to_num(vals, nan=0.0)
    out[~stable] = np.nan
    return out


def lag1_acf(x: np.ndarray) -> float:
    x = np.asarray(x, dtype=float)
    if x.size < 3:
        return np.nan
    a = x[:-1] - np.mean(x[:-1])
    b = x[1:] - np.mean(x[1:])
    den = math.sqrt(float(np.dot(a, a) * np.dot(b, b)))
    return float(np.dot(a, b) / den) if den > 0 else np.nan


def acf_fft(x: np.ndarray, max_lag: int) -> np.ndarray:
    """ACF campionaria normalizzata, calcolata via FFT in O(n log n)."""
    x = np.asarray(x, dtype=np.float64)
    x = x[np.isfinite(x)]
    x = x - x.mean()
    n = x.size
    if n == 0:
        return np.array([])
    size = 1 << (2 * n - 1).bit_length()
    f = np.fft.rfft(x, n=size)
    acov = np.fft.irfft(f * np.conjugate(f), n=size)[: max_lag + 1]
    # divisore n-k -> stima non biased; poi normalizzazione a lag 0
    denom = np.arange(n, n - max_lag - 1, -1, dtype=float)
    acov = acov / denom
    if acov[0] == 0:
        return np.zeros(max_lag + 1)
    return acov / acov[0]


def first_numeric_column(df: pd.DataFrame, preferred: Iterable[str] = ()) -> str:
    for c in preferred:
        if c in df.columns:
            return c
    nums = df.select_dtypes(include=[np.number]).columns.tolist()
    if not nums:
        raise MissingInput("nessuna colonna numerica nel CSV")
    return nums[-1]


def quantiles(x: np.ndarray, probs=(0.50, 0.90, 0.95, 0.99, 0.999)) -> np.ndarray:
    x = np.asarray(x, dtype=float)
    x = x[np.isfinite(x)]
    return np.quantile(x, probs)


def read_trace_valid(csv_dir: Path) -> tuple[pd.DataFrame, float, int]:
    trace = find_file(csv_dir, "trace_visite_job.csv")
    meta = find_file(csv_dir, "job_tracing_metadata.csv")
    md = pd.read_csv(meta)
    measurement_start = float(md.loc[0, "measurement_start"])
    target = int(md.loc[0, "target_valid_jobs"])
    usecols = [
        "Arrival0", "TempoRispostaTotal",
        "Server_1", "RT_1", "Server_2", "RT_2", "Server_3", "RT_3",
        "Server_4", "RT_4", "Server_5", "RT_5",
    ]
    df = pd.read_csv(trace, usecols=usecols)
    df = df[df["Arrival0"] >= measurement_start].head(target).copy()
    if len(df) < target:
        raise MissingInput(f"trace valido insufficiente: {len(df)} < {target}")
    return df, measurement_start, target


# =============================================================================
# 00-01. MODELLO E CAPACITA' TEORICA
# =============================================================================

def plot_service_demands(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    labels = ["Server A", "Server B", "Server P"]
    x = np.arange(3)
    width = 0.36
    y1 = [DEMANDS["1FA"][s] for s in ("A", "B", "P")]
    y2 = [DEMANDS["2FA"][s] for s in ("A", "B", "P")]
    fig, ax = plt.subplots(figsize=(8.0, 4.8))
    ax.bar(x - width / 2, y1, width, label="1FA")
    ax.bar(x + width / 2, y2, width, label="2FA")
    ax.set_xticks(x, labels)
    finish_axis(ax, "Centro di servizio", "Service demand aggregato [s]",
                "Domande di servizio nominali")
    ax.legend()
    save_figure(fig, out_dir, "00_service_demands", dpi)
    return PlotRecord("00_service_demands", "Service demand aggregati 1FA vs 2FA", "parametri analitici", "core")


def plot_theoretical_utilization(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    lam = np.linspace(0.50, 1.40, 300)
    fig, axes = plt.subplots(1, 2, figsize=(12.0, 4.6), sharey=True)
    for ax, scenario in zip(axes, ("1FA", "2FA")):
        for s in ("A", "B", "P"):
            ax.plot(lam, lam * DEMANDS[scenario][s], label=f"U_{s}")
        ax.axhline(1.0, linestyle="--", label="saturazione")
        ax.axvline(1.25, linestyle=":", label="Xmax attuale")
        ax.axvline(LAMBDA_HEAVY, linestyle="-.", label="λ heavy")
        finish_axis(ax, "λ [req/s]", "Utilizzazione teorica", scenario)
        ax.set_ylim(0, 1.12)
    axes[1].legend(loc="upper left", fontsize=8)
    fig.suptitle("Profilo teorico di saturazione dei server")
    save_figure(fig, out_dir, "01_theoretical_utilizations", dpi)
    return PlotRecord("01_theoretical_utilizations", "Utilizzazioni teoriche e soglie di saturazione", "parametri analitici", "core")


# =============================================================================
# 02-05. VALIDAZIONE CONTRO TEORIA
# =============================================================================

def plot_validation_response(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    p = find_file(csv_dir, "validation_scan.csv")
    df = pd.read_csv(p)
    fig, ax = plt.subplots(figsize=(8.2, 5.0))
    for scenario, marker in (("1FA", "o"), ("2FA", "s")):
        d = df[df["scenario"] == scenario].sort_values("lambda")
        lam = d["lambda"].to_numpy()
        ax.plot(lam, d["R"], marker=marker, label=f"Simulazione {scenario}")
        ax.plot(lam, theory_response(lam, DEMANDS[scenario]), linestyle="--",
                label=f"Teoria BCMP {scenario}")
    finish_axis(ax, "λ [req/s]", "E[R] [s]", "Validazione del tempo medio di risposta (modello di riferimento, B Exp)")
    ax.legend(fontsize=8)
    save_figure(fig, out_dir, "02_validation_response_vs_theory", dpi)
    return PlotRecord("02_validation_response_vs_theory", "R simulato vs previsione BCMP", p.name, "core")


def plot_validation_population(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    p = find_file(csv_dir, "validation_scan.csv")
    df = pd.read_csv(p)
    fig, ax = plt.subplots(figsize=(8.2, 5.0))
    for scenario, marker in (("1FA", "o"), ("2FA", "s")):
        d = df[df["scenario"] == scenario].sort_values("lambda")
        lam = d["lambda"].to_numpy()
        ax.plot(lam, d["N_tot"], marker=marker, label=f"Simulazione {scenario}")
        ax.plot(lam, theory_total_population(lam, DEMANDS[scenario]), linestyle="--",
                label=f"Teoria BCMP {scenario}")
    finish_axis(ax, "λ [req/s]", "E[N_tot] [job]", "Validazione della popolazione totale (modello di riferimento, B Exp)")
    ax.legend(fontsize=8)
    save_figure(fig, out_dir, "03_validation_population_vs_theory", dpi)
    return PlotRecord("03_validation_population_vs_theory", "N totale simulato vs teoria", p.name, "core")


def plot_validation_server_metrics(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    p = find_file(csv_dir, "validation_scan.csv")
    df = pd.read_csv(p)
    fig, axes = plt.subplots(2, 3, figsize=(13.0, 7.2), sharex=True)
    servers = ("A", "B", "P")
    for j, s in enumerate(servers):
        ax = axes[0, j]
        for scenario, marker in (("1FA", "o"), ("2FA", "s")):
            d = df[df["scenario"] == scenario].sort_values("lambda")
            lam = d["lambda"].to_numpy()
            ax.plot(lam, d[f"U_{s}"], marker=marker, label=f"Sim {scenario}")
            ax.plot(lam, lam * DEMANDS[scenario][s], linestyle="--", label=f"Teoria {scenario}")
        finish_axis(ax, "λ [req/s]", f"U_{s}", f"Utilizzazione Server {s}")
        if j == 0:
            ax.legend(fontsize=7)

        ax = axes[1, j]
        for scenario, marker in (("1FA", "o"), ("2FA", "s")):
            d = df[df["scenario"] == scenario].sort_values("lambda")
            lam = d["lambda"].to_numpy()
            ax.plot(lam, d[f"X_{s}"], marker=marker, label=f"Sim {scenario}")
            xth = VISITS[s] * lam
            ax.plot(lam, xth, linestyle="--", label=f"Teoria {scenario}")
        finish_axis(ax, "λ [req/s]", f"X_{s} [visite/s]", f"Throughput Server {s}")
    fig.suptitle("Validazione disaggregata: utilizzazioni e throughput (B Exp)")
    fig.tight_layout()
    save_figure(fig, out_dir, "04_validation_server_metrics", dpi)
    return PlotRecord("04_validation_server_metrics", "U e X simulati vs teoria per A/B/P", p.name, "core")


def plot_little_law(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    p = find_file(csv_dir, "validation_scan.csv")
    df = pd.read_csv(p)
    fig, axes = plt.subplots(1, 2, figsize=(12.0, 4.8))
    for scenario, marker in (("1FA", "o"), ("2FA", "s")):
        d = df[df["scenario"] == scenario].sort_values("lambda")
        rhs = d["lambda"] * d["R"]
        axes[0].plot(d["lambda"], d["N_tot"], marker=marker, label=f"N_tot {scenario}")
        axes[0].plot(d["lambda"], rhs, linestyle="--", label=f"λR {scenario}")
        err = 100.0 * (d["N_tot"].to_numpy() - rhs.to_numpy()) / d["N_tot"].to_numpy()
        axes[1].plot(d["lambda"], err, marker=marker, label=scenario)
    finish_axis(axes[0], "λ [req/s]", "job", "Legge di Little: N_tot e λR")
    finish_axis(axes[1], "λ [req/s]", "Scarto relativo [%]", "Errore relativo")
    axes[0].legend(fontsize=8)
    axes[1].legend(fontsize=8)
    fig.tight_layout()
    save_figure(fig, out_dir, "05_validation_little_law", dpi)
    return PlotRecord("05_validation_little_law", "Verifica grafica della Legge di Little", p.name, "core")


# =============================================================================
# 06-09. CONVERGENZA, TRANSITORIO, WELCH, ACF, BATCH SIZE
# =============================================================================

def load_convergence(csv_dir: Path, lam: float) -> tuple[pd.DataFrame, Path]:
    if abs(lam - 0.50) < 1e-12:
        p = find_file(csv_dir, "convergence_lambda0.50.csv", "convergence_lambda0.5.csv")
    elif abs(lam - 1.20) < 1e-12:
        p = find_file(csv_dir, "convergence_lambda1.20.csv", "convergence_lambda1.2.csv")
    else:
        raise ValueError(f"lambda non supportata: {lam}")
    return pd.read_csv(p), p


def _plot_colleagues_convergence(
        csv_dir: Path,
        out_dir: Path,
        dpi: int,
        lam: float,
        stem: str,
) -> PlotRecord:
    """Grafico principale in stile relazione dei colleghi.

    Mostra cinque realizzazioni individuali più la media sulle 64 realizzazioni
    per il tempo di risposta cumulativo R(t) e l'utilizzazione cumulativa U_B(t).
    Le linee teoriche servono come riferimento di convergenza.
    """
    df, p = load_convergence(csv_dir, lam)
    selected = [1, 2, 3, 4, 5]
    theory_r = float(theory_response(np.array([lam]), DEMANDS["1FA"])[0])
    theory_ub = lam * DEMANDS["1FA"]["B"]

    fig, axes = plt.subplots(2, 1, figsize=(11.3, 7.5), sharex=True)

    for r in selected:
        col = f"R_cum_{r}"
        if col in df.columns:
            axes[0].plot(df["t"], df[col], linewidth=0.9, alpha=0.72, label=f"Replica {r-1}")
    axes[0].plot(df["t"], df["R_cum_mean"], linewidth=2.3, label="Media su 64 realizzazioni")
    axes[0].axhline(theory_r, linewidth=1.2, linestyle="--", label=f"Teoria = {theory_r:.3f} s")
    axes[0].axvline(WARMUP_TIME, linewidth=1.1, linestyle=":", label="Warm-up = 100000 s")
    finish_axis(axes[0], "", "R cumulativo [s]", "Tempo medio di risposta")
    axes[0].legend(fontsize=7, ncol=4)

    for r in selected:
        col = f"U_B_cum_{r}"
        if col in df.columns:
            axes[1].plot(df["t"], df[col], linewidth=0.9, alpha=0.72, label=f"Replica {r-1}")
    axes[1].plot(df["t"], df["U_B_cum_mean"], linewidth=2.3, label="Media su 64 realizzazioni")
    axes[1].axhline(theory_ub, linewidth=1.2, linestyle="--", label=f"Teoria = {theory_ub:.3f}")
    axes[1].axvline(WARMUP_TIME, linewidth=1.1, linestyle=":", label="Warm-up = 100000 s")
    finish_axis(axes[1], "Tempo simulato [s]", "U_B cumulativa", "Utilizzazione del Server B")
    axes[1].legend(fontsize=7, ncol=4)

    fig.suptitle(
        f"Convergenza multi-replica - 1FA, B HyperExp, λ = {lam:.2f} req/s\n"
        "5 repliche visualizzate; stime aggregate su 64 realizzazioni"
    )
    fig.tight_layout()
    save_figure(fig, out_dir, stem, dpi)
    return PlotRecord(
        stem,
        f"Convergenza multi-replica di R e U_B a lambda={lam:.2f}",
        p.name,
        "core",
    )


def plot_convergence_low_load(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    return _plot_colleagues_convergence(
        csv_dir, out_dir, dpi, 0.50, "06a_convergence_lambda0.50_R_UB"
    )


def plot_convergence_critical(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    return _plot_colleagues_convergence(
        csv_dir, out_dir, dpi, 1.20, "06b_convergence_lambda1.20_R_UB"
    )


def plot_convergence_window_means(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    """Medie locali per finestra, con smoothing, per evidenziare l'assestamento.

    I CSV contengono statistiche su finestre da 100 s. La rolling mean non viene
    usata per l'inferenza: serve solo come visualizzazione del transitorio locale.
    """
    d05, p05 = load_convergence(csv_dir, 0.50)
    d12, p12 = load_convergence(csv_dir, 1.20)
    smooth_windows = 50  # 50*100s = 5000 s

    fig, axes = plt.subplots(2, 2, figsize=(13.2, 7.8), sharex="col")
    for col_idx, (df, lam) in enumerate(((d05, 0.50), (d12, 1.20))):
        theory_r = float(theory_response(np.array([lam]), DEMANDS["1FA"])[0])
        theory_ub = lam * DEMANDS["1FA"]["B"]

        r_sm = df["R_window_mean"].rolling(smooth_windows, center=True, min_periods=10).mean()
        u_sm = df["U_B_window_mean"].rolling(smooth_windows, center=True, min_periods=10).mean()

        axes[0, col_idx].plot(df["t"], df["R_window_mean"], linewidth=0.65, alpha=0.45, label="Media finestra, 64 repliche")
        axes[0, col_idx].plot(df["t"], r_sm, linewidth=2.0, label="Media mobile 5000 s")
        axes[0, col_idx].axhline(theory_r, linestyle="--", label="Teoria")
        axes[0, col_idx].axvline(WARMUP_TIME, linestyle=":", label="Warm-up")
        finish_axis(axes[0, col_idx], "", "R finestra [s]", f"R locale, λ={lam:.2f}")
        axes[0, col_idx].legend(fontsize=7)

        axes[1, col_idx].plot(df["t"], df["U_B_window_mean"], linewidth=0.65, alpha=0.45, label="Media finestra, 64 repliche")
        axes[1, col_idx].plot(df["t"], u_sm, linewidth=2.0, label="Media mobile 5000 s")
        axes[1, col_idx].axhline(theory_ub, linestyle="--", label="Teoria")
        axes[1, col_idx].axvline(WARMUP_TIME, linestyle=":", label="Warm-up")
        finish_axis(axes[1, col_idx], "Tempo simulato [s]", "U_B finestra", f"U_B locale, λ={lam:.2f}")
        axes[1, col_idx].legend(fontsize=7)

    fig.suptitle("Assestamento locale delle statistiche - medie su 64 realizzazioni")
    fig.tight_layout()
    save_figure(fig, out_dir, "06c_convergence_window_means", dpi)
    return PlotRecord(
        "06c_convergence_window_means",
        "Statistiche locali R/U_B su finestre da 100 s e smoothing visuale",
        f"{p05.name}; {p12.name}",
        "supplementary",
    )


def plot_convergence_final_vs_theory(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    p = find_file(csv_dir, "convergence_visualization_summary.csv")
    df = pd.read_csv(p)
    metrics = ["R", "U_B", "N_B"]
    fig, axes = plt.subplots(1, 3, figsize=(13.2, 4.4))

    for ax, metric in zip(axes, metrics):
        d = df[df["metric"] == metric].sort_values("lambda")
        if len(d) != 2:
            raise MissingInput(f"summary convergenza: metriche {metric} incomplete")
        x = np.arange(len(d))
        ax.errorbar(x, d["mean"], yerr=d["half_width_95"], marker="o", capsize=5, label="Simulazione ± IC 95%")
        ax.scatter(x, d["theory"], marker="x", s=65, label="Teoria")
        ax.set_xticks(x, [f"λ={v:.2f}" for v in d["lambda"]])
        finish_axis(ax, "Carico", metric, metric)
        ax.legend(fontsize=7)

    fig.suptitle("Convergenza: stima finale su 64 realizzazioni vs valore teorico")
    fig.tight_layout()
    save_figure(fig, out_dir, "06d_convergence_final_vs_theory", dpi)
    return PlotRecord(
        "06d_convergence_final_vs_theory",
        "Stime finali R/U_B/N_B con IC 95% confrontate con la teoria",
        p.name,
        "core",
    )


def export_convergence_seed_table(csv_dir: Path, out_dir: Path) -> Path:
    """Esporta le prime 5 repliche e i 6 stream in formato tabellare.

    È l'equivalente della tabella dei seed mostrata sotto i grafici di transitorio
    nella relazione dei colleghi. Non è un grafico e non entra nel manifest.
    """
    p = find_file(csv_dir, "convergence_visualization_rng_seeds.csv")
    df = pd.read_csv(p)
    df = df[df["repetition"].between(0, 4)].copy()
    table = df.pivot_table(
        index=["configuration", "repetition"],
        columns="stream",
        values="seed",
        aggfunc="first",
    ).reset_index()
    table.columns = [
        "configuration" if c == "configuration" else
        "repetition" if c == "repetition" else
        f"stream_{int(c)}"
        for c in table.columns
    ]
    out = out_dir / "convergence_seed_table_first5.csv"
    table.to_csv(out, index=False)
    return out


def plot_transient_replications(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    """Show a readable subset of individual transient realizations.

    The numerical analysis uses all 64 realizations, but only five are drawn so
    that stochastic variability remains visible without producing a spaghetti plot.
    """
    p05 = find_file(csv_dir, "transient_analysis_lambda0.5_trajectories.csv")
    p12 = find_file(csv_dir, "transient_analysis_lambda1.2_trajectories.csv")

    fig, axes = plt.subplots(1, 2, figsize=(13.2, 5.0), sharex=True)
    selected = [1, 2, 3, 4, 5]

    for ax, p, lam in ((axes[0], p05, 0.50), (axes[1], p12, 1.20)):
        df = pd.read_csv(p)
        available = [f"N_B_{i}" for i in selected if f"N_B_{i}" in df.columns]
        if len(available) < 2:
            raise ValueError(f"In {p.name} non ci sono abbastanza traiettorie N_B_i")

        for idx, col in enumerate(available):
            ax.plot(
                df["t"], df[col], linewidth=0.75, alpha=0.58,
                label=f"Replica {idx}"
            )

        all_cols = [c for c in df.columns if c.startswith("N_B_") and c != "N_B_mean"]
        mean = df["N_B_mean"] if "N_B_mean" in df.columns else df[all_cols].mean(axis=1)
        theory_nb = (lam * 0.8) / (1.0 - lam * 0.8)
        ax.plot(df["t"], mean, linewidth=2.0, label="Media su 64 realizzazioni")
        ax.axhline(theory_nb, linestyle="--", linewidth=1.2,
                   label=f"E[N_B] teorico = {theory_nb:.2f}")
        ax.axvline(WARMUP_TIME, linestyle=":", linewidth=1.2,
                   label="Warm-up = 100000 s")
        finish_axis(ax, "Tempo simulato [s]", "N_B medio nella finestra",
                    f"Traiettorie individuali, λ = {lam:.2f} req/s")
        ax.legend(fontsize=7, ncol=2)

    fig.suptitle(
        "Variabilità tra realizzazioni del Server B\n"
        "(5 traiettorie mostrate; stime aggregate calcolate su 64 realizzazioni)"
    )
    fig.tight_layout()
    save_figure(fig, out_dir, "06e_transient_selected_replications_NB", dpi)
    return PlotRecord(
        "06e_transient_selected_replications_NB",
        "Cinque traiettorie individuali e media su 64 realizzazioni",
        f"{p05.name}; {p12.name}",
        "core",
    )


def plot_transient_running_means(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    """Colleagues-style convergence plot based on cumulative time averages.

    The transient files store equal-width 100 s window averages. Their expanding
    mean is therefore the cumulative time average from t=0 to the current time,
    which is appropriate for visualizing convergence of each realization.
    """
    p05 = find_file(csv_dir, "transient_analysis_lambda0.5_trajectories.csv")
    p12 = find_file(csv_dir, "transient_analysis_lambda1.2_trajectories.csv")

    fig, axes = plt.subplots(1, 2, figsize=(13.2, 5.0), sharex=True)
    selected = [1, 2, 3, 4, 5]

    for ax, p, lam in ((axes[0], p05, 0.50), (axes[1], p12, 1.20)):
        df = pd.read_csv(p)
        all_cols = [c for c in df.columns if c.startswith("N_B_") and c != "N_B_mean"]
        chosen = [f"N_B_{i}" for i in selected if f"N_B_{i}" in df.columns]
        if len(chosen) < 2:
            raise ValueError(f"In {p.name} non ci sono abbastanza traiettorie N_B_i")

        for idx, col in enumerate(chosen):
            running = df[col].expanding(min_periods=1).mean()
            ax.plot(df["t"], running, linewidth=1.0, alpha=0.78,
                    label=f"Replica {idx}")

        ensemble_window_mean = df[all_cols].mean(axis=1)
        ensemble_running = ensemble_window_mean.expanding(min_periods=1).mean()
        theory_nb = (lam * 0.8) / (1.0 - lam * 0.8)
        ax.plot(df["t"], ensemble_running, linewidth=2.4,
                label="Media cumulativa su 64 realizzazioni")
        ax.axhline(theory_nb, linestyle="--", linewidth=1.2,
                   label=f"E[N_B] teorico = {theory_nb:.2f}")
        ax.axvline(WARMUP_TIME, linestyle=":", linewidth=1.2,
                   label="Warm-up = 100000 s")
        finish_axis(ax, "Tempo simulato [s]", "Media temporale cumulativa di N_B",
                    f"Convergenza per replica, λ = {lam:.2f} req/s")
        ax.legend(fontsize=7, ncol=2)

    fig.suptitle(
        "Convergenza delle statistiche nelle diverse realizzazioni\n"
        "(5 repliche visualizzate; media cumulativa d'insieme su 64 realizzazioni)"
    )
    fig.tight_layout()
    save_figure(fig, out_dir, "06f_transient_running_means_NB", dpi)
    return PlotRecord(
        "06f_transient_running_means_NB",
        "Medie temporali cumulative di cinque repliche e media d'insieme su 64",
        f"{p05.name}; {p12.name}",
        "core",
    )


def plot_welch(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    p05 = find_file(csv_dir, "transient_analysis_lambda0.5_trajectories.csv")
    p12 = find_file(csv_dir, "transient_analysis_lambda1.2_trajectories.csv")
    fig, axes = plt.subplots(1, 2, figsize=(13.0, 4.9), sharex=True)
    for ax, p, lam in ((axes[0], p05, 0.50), (axes[1], p12, 1.20)):
        df = pd.read_csv(p)
        mean = df["N_B_mean"] if "N_B_mean" in df.columns else df.filter(like="N_B_").mean(axis=1)
        welch = mean.rolling(
            window=2 * WELCH_HALF_WINDOW + 1,
            center=True,
            min_periods=WELCH_HALF_WINDOW + 1,
        ).mean()
        theory_nb = (lam * 0.8) / (1.0 - lam * 0.8)
        ax.plot(df["t"], mean, linewidth=0.8, alpha=0.55, label="Media d'insieme")
        ax.plot(df["t"], welch, linewidth=1.8, label="Welch smussata")
        ax.axhline(theory_nb, linestyle="--", label=f"E[N_B] teorico = {theory_nb:.2f}")
        ax.axvline(WARMUP_TIME, linestyle=":", label="Warm-up = 100000 s")
        finish_axis(ax, "Tempo [s]", "N_B medio", f"λ = {lam:.2f} req/s")
        ax.legend(fontsize=7)
    fig.suptitle("Analisi del transitorio e scelta del warm-up (64 traiettorie)")
    fig.tight_layout()
    save_figure(fig, out_dir, "06g_welch_transient", dpi)
    return PlotRecord("06g_welch_transient", "Welch a λ=0.5 e λ=1.2 con warm-up scelto", f"{p05.name}; {p12.name}", "core")


def load_acf_sequence(csv_dir: Path) -> tuple[np.ndarray, Path]:
    p = find_file(csv_dir, "response_times_B_HyperExp_acf.csv")
    df = pd.read_csv(p)
    c = first_numeric_column(df, ("response_time_B", "value"))
    return df[c].to_numpy(dtype=float), p


def plot_acf(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    x, p = load_acf_sequence(csv_dir)
    max_lag = min(ACF_MAX_LAG, len(x) - 1)
    acf = acf_fft(x, max_lag)
    conf = 1.96 / math.sqrt(len(x))
    lags = np.arange(acf.size)
    fig, axes = plt.subplots(1, 2, figsize=(13.0, 4.7))
    axes[0].plot(lags[1:5001], acf[1:5001], linewidth=0.8)
    axes[0].axhline(conf, linestyle="--")
    axes[0].axhline(-conf, linestyle="--")
    axes[0].axhline(0.0, linewidth=0.8)
    finish_axis(axes[0], "Lag", "ACF", "Dettaglio: lag 0-5000")
    axes[1].plot(lags[1:], acf[1:], linewidth=0.7)
    axes[1].axhline(conf, linestyle="--", label="±1.96/√N")
    axes[1].axhline(-conf, linestyle="--")
    axes[1].axhline(0.0, linewidth=0.8)
    finish_axis(axes[1], "Lag", "ACF", "Vista estesa")
    axes[1].legend(fontsize=8)
    fig.suptitle("Autocorrelazione dei tempi di permanenza al Server B")
    fig.tight_layout()
    save_figure(fig, out_dir, "07_acf_server_B", dpi)
    return PlotRecord("07_acf_server_B", "ACF raw dei tempi B con banda 95%", p.name, "core")


def batch_means(x: np.ndarray, b: int) -> np.ndarray:
    m = len(x) // b
    if m <= 0:
        return np.array([])
    return x[: m * b].reshape(m, b).mean(axis=1)


def plot_batch_diagnostic(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    x, p = load_acf_sequence(csv_dir)
    sizes = np.array([1000, 2000, 4000, 8000, 16000], dtype=int)
    rho = []
    band = []
    batches_count = []
    for b in sizes:
        bm = batch_means(x, int(b))
        rho.append(abs(lag1_acf(bm)))
        band.append(1.96 / math.sqrt(len(bm)) if len(bm) else np.nan)
        batches_count.append(len(bm))
    rho = np.asarray(rho)
    band = np.asarray(band)

    fig, axes = plt.subplots(1, 2, figsize=(12.0, 4.7))
    axes[0].plot(sizes, rho, marker="o", label="|ACF lag 1| delle batch means")
    axes[0].plot(sizes, band, marker="s", linestyle="--", label="1.96/√m")
    axes[0].axvline(CHOSEN_BATCH_SIZE, linestyle=":", label="b scelto = 8000")
    finish_axis(axes[0], "Batch size b [job]", "Valore assoluto", "Dipendenza residua tra batch")
    axes[0].set_xscale("log", base=2)
    axes[0].legend(fontsize=8)

    axes[1].plot(sizes, batches_count, marker="o")
    axes[1].axvline(CHOSEN_BATCH_SIZE, linestyle=":")
    finish_axis(axes[1], "Batch size b [job]", "Numero di batch m", "Trade-off: indipendenza vs numerosità")
    axes[1].set_xscale("log", base=2)
    fig.suptitle("Diagnostica per la scelta della dimensione dei batch")
    fig.tight_layout()
    save_figure(fig, out_dir, "08_batch_size_diagnostic", dpi)
    return PlotRecord("08_batch_size_diagnostic", "ACF lag-1 e numerosità al variare di b", p.name, "core")


def plot_batch_means_series(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    x, p = load_acf_sequence(csv_dir)
    bm = batch_means(x, CHOSEN_BATCH_SIZE)
    mean = float(np.mean(bm))
    sd = float(np.std(bm, ddof=1))
    # fascia descrittiva, non IC sulla media: ±1.96 sd delle batch means
    fig, ax = plt.subplots(figsize=(9.0, 4.8))
    ax.plot(np.arange(1, len(bm) + 1), bm, marker="o", markersize=3, linewidth=0.8)
    ax.axhline(mean, linestyle="--", label=f"media batch = {mean:.3f} s")
    ax.axhline(mean + 1.96 * sd, linestyle=":", label="media ± 1.96·sd(batch)")
    ax.axhline(mean - 1.96 * sd, linestyle=":")
    finish_axis(ax, "Indice batch", "Tempo medio a B [s]", f"Batch means con b = {CHOSEN_BATCH_SIZE}")
    ax.legend(fontsize=8)
    save_figure(fig, out_dir, "09_batch_means_series_b8000", dpi)
    return PlotRecord("09_batch_means_series_b8000", "Sequenza delle batch means per b=8000", p.name, "supplementary")


# =============================================================================
# 10-13. RISULTATI A REGIME 1FA VS 2FA
# =============================================================================

def load_regime(csv_dir: Path) -> tuple[pd.DataFrame, Path]:
    p = find_file(csv_dir, "regime_experiment.csv", "regime_experiment(1).csv")
    return pd.read_csv(p), p


def plot_regime_response(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    df, p = load_regime(csv_dir)
    fig, ax = plt.subplots(figsize=(8.4, 5.1))
    for scenario, marker in (("1FA", "o"), ("2FA", "s")):
        d = df[df["scenario"] == scenario].sort_values("lambda")
        ax.errorbar(d["lambda"], d["R_mean"], yerr=d["R_hw"], marker=marker,
                    capsize=2.5, label=scenario)
    ax.axvline(1.25, linestyle="--", label="Xmax attuale = 1.25 req/s")
    finish_axis(ax, "λ [req/s]", "E[R] [s]", "Tempo medio di risposta a regime (B HyperExp, IC 95%)")
    ax.legend(fontsize=8)
    save_figure(fig, out_dir, "10_regime_response_1FA_vs_2FA", dpi)
    return PlotRecord("10_regime_response_1FA_vs_2FA", "R a regime 1FA vs 2FA con IC95", p.name, "core")


def plot_regime_populations(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    df, p = load_regime(csv_dir)
    fig, axes = plt.subplots(1, 3, figsize=(14.0, 4.7), sharex=True)
    for ax, s in zip(axes, ("A", "B", "P")):
        for scenario, marker in (("1FA", "o"), ("2FA", "s")):
            d = df[df["scenario"] == scenario].sort_values("lambda")
            ax.errorbar(d["lambda"], d[f"N_{s}_mean"], yerr=d[f"N_{s}_hw"],
                        marker=marker, capsize=2, label=scenario)
        finish_axis(ax, "λ [req/s]", f"E[N_{s}] [job]", f"Server {s}")
        ax.legend(fontsize=7)
    fig.suptitle("Popolazioni medie a regime (B HyperExp, IC 95%)")
    fig.tight_layout()
    save_figure(fig, out_dir, "11_regime_populations", dpi)
    return PlotRecord("11_regime_populations", "N_A, N_B, N_P a regime 1FA vs 2FA", p.name, "core")


def plot_regime_utilizations(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    df, p = load_regime(csv_dir)
    fig, axes = plt.subplots(1, 3, figsize=(14.0, 4.7), sharex=True, sharey=True)
    for ax, s in zip(axes, ("A", "B", "P")):
        for scenario, marker in (("1FA", "o"), ("2FA", "s")):
            d = df[df["scenario"] == scenario].sort_values("lambda")
            ax.errorbar(d["lambda"], d[f"U_{s}_mean"], yerr=d[f"U_{s}_hw"],
                        marker=marker, capsize=2, label=scenario)
        ax.axhline(1.0, linestyle="--")
        finish_axis(ax, "λ [req/s]", f"U_{s}", f"Server {s}")
        ax.legend(fontsize=7)
    fig.suptitle("Utilizzazioni a regime (B HyperExp, IC 95%)")
    fig.tight_layout()
    save_figure(fig, out_dir, "12_regime_utilizations", dpi)
    return PlotRecord("12_regime_utilizations", "U_A, U_B, U_P a regime", p.name, "core")


def plot_regime_throughputs(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    df, p = load_regime(csv_dir)
    fig, axes = plt.subplots(1, 3, figsize=(14.0, 4.7), sharex=True)
    for ax, s in zip(axes, ("A", "B", "P")):
        for scenario, marker in (("1FA", "o"), ("2FA", "s")):
            d = df[df["scenario"] == scenario].sort_values("lambda")
            ax.errorbar(d["lambda"], d[f"X_{s}_mean"], yerr=d[f"X_{s}_hw"],
                        marker=marker, capsize=2, label=scenario)
        finish_axis(ax, "λ [req/s]", f"X_{s} [visite/s]", f"Server {s}")
        ax.legend(fontsize=7)
    fig.suptitle("Throughput a regime (B HyperExp, IC 95%)")
    fig.tight_layout()
    save_figure(fig, out_dir, "13_regime_throughputs", dpi)
    return PlotRecord("13_regime_throughputs", "X_A, X_B, X_P a regime", p.name, "supplementary")


# =============================================================================
# 14-17. EXP VS HYPEREXP A REGIME
# =============================================================================

def load_exp_hyper_summary(csv_dir: Path) -> tuple[pd.DataFrame, Path]:
    p = find_file(csv_dir, "exp_vs_hyperexp_comparison.csv")
    return pd.read_csv(p), p


def load_response_values(csv_dir: Path, label: str) -> tuple[np.ndarray, Path]:
    p = find_file(csv_dir, f"response_times_B_{label}.csv")
    df = pd.read_csv(p)
    c = first_numeric_column(df, ("value", "response_time_B"))
    return df[c].to_numpy(dtype=float), p


def plot_exp_hyper_aggregate(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    df, p = load_exp_hyper_summary(csv_dir)
    labels = df["distribution"].tolist()
    fig, axes = plt.subplots(2, 2, figsize=(10.5, 7.8))
    specs = [
        ("R_mean", "R_hw", "E[R] [s]", "Tempo di risposta sistema"),
        ("N_B_mean", "N_B_hw", "E[N_B] [job]", "Popolazione Server B"),
        ("U_B_mean", "U_B_hw", "U_B", "Utilizzazione Server B"),
        ("X_B_mean", "X_B_hw", "X_B [req/s]", "Throughput Server B"),
    ]
    for ax, (m, hw, yl, title) in zip(axes.ravel(), specs):
        ax.bar(labels, df[m], yerr=df[hw], capsize=5)
        finish_axis(ax, "Distribuzione del servizio B", yl, title)
    fig.suptitle("Exp vs HyperExp: metriche medie a regime (IC 95%)")
    fig.tight_layout()
    save_figure(fig, out_dir, "14_exp_vs_hyperexp_aggregate", dpi)
    return PlotRecord("14_exp_vs_hyperexp_aggregate", "Confronto delle medie a regime Exp/H2", p.name, "core")


def plot_exp_hyper_hist(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    exp, p1 = load_response_values(csv_dir, "Exp")
    hyp, p2 = load_response_values(csv_dir, "HyperExp")
    upper = max(np.quantile(exp, 0.995), np.quantile(hyp, 0.995))
    bins = np.linspace(0, upper, 100)
    fig, ax = plt.subplots(figsize=(8.5, 5.0))
    ax.hist(exp, bins=bins, density=True, histtype="step", linewidth=1.5, label="Exp")
    ax.hist(hyp, bins=bins, density=True, histtype="step", linewidth=1.5, label="HyperExp")
    ax.set_yscale("log")
    finish_axis(ax, "Tempo di permanenza a B [s]", "Densità empirica (scala log)",
                "Distribuzione dei tempi individuali al Server B")
    ax.legend()
    save_figure(fig, out_dir, "15_exp_vs_hyperexp_histogram", dpi)
    return PlotRecord("15_exp_vs_hyperexp_histogram", "Istogramma/densità empirica Exp vs H2", f"{p1.name}; {p2.name}", "core")


def empirical_cdf_points(x: np.ndarray, max_points: int = 6000) -> tuple[np.ndarray, np.ndarray]:
    sx = np.sort(x[np.isfinite(x)])
    n = len(sx)
    if n == 0:
        return sx, sx
    idx = np.unique(np.linspace(0, n - 1, min(n, max_points)).astype(int))
    return sx[idx], (idx + 1) / n


def plot_exp_hyper_cdf_ccdf(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    exp, p1 = load_response_values(csv_dir, "Exp")
    hyp, p2 = load_response_values(csv_dir, "HyperExp")
    fig, axes = plt.subplots(1, 2, figsize=(13.0, 4.8))
    for x, label in ((exp, "Exp"), (hyp, "HyperExp")):
        sx, cdf = empirical_cdf_points(x)
        axes[0].plot(sx, cdf, label=label)
        ccdf = np.maximum(1.0 - cdf, 1.0 / len(x))
        axes[1].plot(sx, ccdf, label=label)
    q995 = max(np.quantile(exp, 0.995), np.quantile(hyp, 0.995))
    axes[0].set_xlim(0, q995)
    finish_axis(axes[0], "Tempo a B [s]", "F(t)", "ECDF (fino al P99.5)")
    axes[1].set_yscale("log")
    axes[1].set_xscale("log")
    finish_axis(axes[1], "Tempo a B [s] (log)", "P(T_B > t) (log)", "CCDF: comportamento di coda")
    axes[0].legend()
    axes[1].legend()
    fig.suptitle("Exp vs HyperExp: distribuzione e coda dei tempi al Server B")
    fig.tight_layout()
    save_figure(fig, out_dir, "16_exp_vs_hyperexp_ecdf_ccdf", dpi)
    return PlotRecord("16_exp_vs_hyperexp_ecdf_ccdf", "ECDF e CCDF log-log Exp/H2", f"{p1.name}; {p2.name}", "core")


def plot_exp_hyper_percentiles(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    exp, p1 = load_response_values(csv_dir, "Exp")
    hyp, p2 = load_response_values(csv_dir, "HyperExp")
    probs = np.array([0.50, 0.90, 0.95, 0.99, 0.999])
    labels = ["P50", "P90", "P95", "P99", "P99.9"]
    qe = quantiles(exp, probs)
    qh = quantiles(hyp, probs)
    x = np.arange(len(labels))
    width = 0.36
    fig, ax = plt.subplots(figsize=(9.0, 5.0))
    ax.bar(x - width / 2, qe, width, label="Exp")
    ax.bar(x + width / 2, qh, width, label="HyperExp")
    ax.set_xticks(x, labels)
    ax.set_yscale("log")
    finish_axis(ax, "Percentile", "Tempo a B [s] (scala log)", "Percentili dei tempi al Server B")
    ax.legend()
    save_figure(fig, out_dir, "17_exp_vs_hyperexp_percentiles", dpi)
    return PlotRecord("17_exp_vs_hyperexp_percentiles", "P50-P99.9 dei tempi B", f"{p1.name}; {p2.name}", "core")


# =============================================================================
# 18-19. JOB TRACING
# =============================================================================

def plot_job_trace_decomposition(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    df, measurement_start, target = read_trace_valid(csv_dir)
    stage_labels = ["A1", "B", "A2", "P", "A3"]
    means = np.array([df[f"RT_{i}"].mean() for i in range(1, 6)])
    total = float(df["TempoRispostaTotal"].mean())
    pct = 100.0 * means / means.sum()

    fig, axes = plt.subplots(1, 2, figsize=(12.0, 4.8))
    axes[0].bar(stage_labels, means)
    finish_axis(axes[0], "Visita", "Permanenza media [s]", "Decomposizione del response time")
    axes[0].axhline(total, linestyle=":", label=f"R totale = {total:.3f} s")
    axes[0].legend(fontsize=8)

    axes[1].bar(stage_labels, pct)
    finish_axis(axes[1], "Visita", "Contributo a R [%]", "Quota percentuale per visita")
    for i, v in enumerate(pct):
        axes[1].text(i, v, f"{v:.1f}%", ha="center", va="bottom", fontsize=8)

    fig.suptitle(f"Job tracing post warm-up: primi {target:,} job validi")
    fig.tight_layout()
    save_figure(fig, out_dir, "18_job_trace_decomposition", dpi)
    return PlotRecord("18_job_trace_decomposition", "Decomposizione A1-B-A2-P-A3 del response time", "trace_visite_job.csv; job_tracing_metadata.csv", "core")


def plot_job_trace_percentiles(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    df, _, _ = read_trace_valid(csv_dir)
    stages = ["A1", "B", "A2", "P", "A3"]
    probs = [0.50, 0.90, 0.95, 0.99]
    fig, ax = plt.subplots(figsize=(9.0, 5.2))
    for p in probs:
        vals = [df[f"RT_{i}"].quantile(p) for i in range(1, 6)]
        ax.plot(stages, vals, marker="o", label=f"P{int(p*100)}")
    ax.set_yscale("log")
    finish_axis(ax, "Visita", "Permanenza [s] (scala log)", "Percentili per tappa del workflow")
    ax.legend()
    save_figure(fig, out_dir, "19_job_trace_stage_percentiles", dpi)
    return PlotRecord("19_job_trace_stage_percentiles", "Percentili delle cinque visite", "trace_visite_job.csv", "supplementary")


# =============================================================================
# 20-23. HEAVY LOAD
# =============================================================================

def load_heavy_nominal(csv_dir: Path) -> tuple[pd.DataFrame, Path]:
    p = find_file(csv_dir, "heavy_load_experiment.csv")
    return pd.read_csv(p), p


def load_heavy_comparison(csv_dir: Path) -> tuple[pd.DataFrame, pd.DataFrame, Path, Path]:
    pe = find_file(csv_dir, "heavy_Exp.csv", "heavy_Exp(1).csv")
    ph = find_file(csv_dir, "heavy_HyperExp.csv", "heavy_HyperExp(1).csv")
    return pd.read_csv(pe), pd.read_csv(ph), pe, ph


def plot_heavy_populations(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    df, p = load_heavy_nominal(csv_dir)
    fig, axes = plt.subplots(1, 2, figsize=(13.0, 4.9))
    # B separato per non schiacciare A e P
    axes[0].plot(df["t"], df["N_B_mean"], label="N_B")
    axes[0].fill_between(df["t"], df["N_B_mean"] - df["N_B_hw"], df["N_B_mean"] + df["N_B_hw"], alpha=0.18)
    finish_axis(axes[0], "Tempo [s]", "N_B medio [job]", "Collo di bottiglia B")
    axes[0].legend()

    for s in ("A", "P"):
        axes[1].plot(df["t"], df[f"N_{s}_mean"], label=f"N_{s}")
        axes[1].fill_between(df["t"], df[f"N_{s}_mean"] - df[f"N_{s}_hw"], df[f"N_{s}_mean"] + df[f"N_{s}_hw"], alpha=0.15)
    finish_axis(axes[1], "Tempo [s]", "Popolazione media [job]", "Nodi non divergenti")
    axes[1].legend()
    fig.suptitle("Heavy load nominale (B HyperExp): evoluzione delle popolazioni (IC 95%)")
    fig.tight_layout()
    save_figure(fig, out_dir, "20_heavy_load_populations", dpi)
    return PlotRecord("20_heavy_load_populations", "Heavy nominale: B divergente, A/P limitati", p.name, "core")


def plot_heavy_bottleneck(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    df, p = load_heavy_nominal(csv_dir)
    x = df["t"].to_numpy()
    y = df["N_B_mean"].to_numpy()
    slope, intercept = np.polyfit(x, y, 1)
    fit = slope * x + intercept
    fig, ax = plt.subplots(figsize=(8.8, 5.0))
    ax.plot(x, y, label="N_B medio")
    ax.fill_between(x, y - df["N_B_hw"], y + df["N_B_hw"], alpha=0.18, label="IC 95%")
    ax.plot(x, fit, linestyle="--", label=f"fit lineare: {slope:.5f} job/s")
    finish_axis(ax, "Tempo [s]", "N_B medio [job]", "Divergenza del collo di bottiglia sotto overload")
    ax.legend(fontsize=8)
    save_figure(fig, out_dir, "21_heavy_load_bottleneck_growth", dpi)
    return PlotRecord("21_heavy_load_bottleneck_growth", "Crescita N_B e regressione lineare", p.name, "core")


def plot_heavy_exp_hyper(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    de, dh, pe, ph = load_heavy_comparison(csv_dir)
    fig, ax = plt.subplots(figsize=(9.0, 5.1))
    for d, label in ((de, "Exp"), (dh, "HyperExp")):
        slope, _ = np.polyfit(d["t"].to_numpy(), d["N_B_mean"].to_numpy(), 1)
        ax.plot(d["t"], d["N_B_mean"], label=f"{label} (pendenza ≈ {slope:.4f} job/s)")
        ax.fill_between(d["t"], d["N_B_mean"] - d["N_B_hw"], d["N_B_mean"] + d["N_B_hw"], alpha=0.15)
    finish_axis(ax, "Tempo [s]", "N_B medio [job]", "Overload: Exp vs HyperExp al Server B")
    ax.legend(fontsize=8)
    save_figure(fig, out_dir, "22_heavy_exp_vs_hyperexp", dpi)
    return PlotRecord("22_heavy_exp_vs_hyperexp", "Dinamica N_B Exp vs HyperExp in overload", f"{pe.name}; {ph.name}", "core")


def plot_heavy_final_comparison(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    de, dh, pe, ph = load_heavy_comparison(csv_dir)
    last_e = de.iloc[-1]
    last_h = dh.iloc[-1]
    labels = ["Exp", "HyperExp"]
    means = [last_e["N_B_mean"], last_h["N_B_mean"]]
    hws = [last_e["N_B_hw"], last_h["N_B_hw"]]
    slopes = [
        np.polyfit(de["t"], de["N_B_mean"], 1)[0],
        np.polyfit(dh["t"], dh["N_B_mean"], 1)[0],
    ]
    fig, axes = plt.subplots(1, 2, figsize=(11.0, 4.7))
    axes[0].bar(labels, means, yerr=hws, capsize=5)
    finish_axis(axes[0], "Distribuzione", "N_B(150000) [job]", "Popolazione finale (IC 95%)")
    axes[1].bar(labels, slopes)
    finish_axis(axes[1], "Distribuzione", "Pendenza [job/s]", "Tasso empirico di accumulo")
    fig.suptitle("Sintesi heavy load: intensità dell'accumulo al Server B")
    fig.tight_layout()
    save_figure(fig, out_dir, "23_heavy_final_and_growth_rate", dpi)
    return PlotRecord("23_heavy_final_and_growth_rate", "N_B finale e pendenza Exp/H2", f"{pe.name}; {ph.name}", "supplementary")


# =============================================================================
# 24-26. CAPACITY PLANNING ANALITICO
# =============================================================================

def throughput_bound(demands: dict[str, float]) -> float:
    return 1.0 / max(demands.values())


def plot_capacity_bounds(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    scenarios = ["1FA", "2FA"]
    old = [throughput_bound(DEMANDS[s]) for s in scenarios]
    new = [throughput_bound(DEMANDS_UPGRADED_B[s]) for s in scenarios]
    x = np.arange(2)
    width = 0.36
    fig, ax = plt.subplots(figsize=(8.5, 4.9))
    ax.bar(x - width / 2, old, width, label="Configurazione attuale")
    ax.bar(x + width / 2, new, width, label="B potenziato: D_B=0.4 s")
    ax.axhline(LAMBDA_HEAVY, linestyle="--", label="Carico heavy λ=1.4")
    ax.set_xticks(x, scenarios)
    finish_axis(ax, "Scenario", "Throughput bound Xmax [req/s]", "Capacity planning: capacità massima teorica")
    ax.legend(fontsize=8)
    save_figure(fig, out_dir, "24_capacity_planning_bounds", dpi)
    return PlotRecord("24_capacity_planning_bounds", "Xmax prima/dopo upgrade B", "analisi what-if", "core")


def plot_capacity_utilization_heavy(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    fig, axes = plt.subplots(2, 2, figsize=(11.0, 7.5), sharey=True)
    for row, scenario in enumerate(("1FA", "2FA")):
        for col, (label, demands) in enumerate((
                ("Attuale", DEMANDS[scenario]),
                ("B potenziato", DEMANDS_UPGRADED_B[scenario]),
        )):
            ax = axes[row, col]
            servers = ["A", "B", "P"]
            vals = [LAMBDA_HEAVY * demands[s] for s in servers]
            ax.bar(servers, vals)
            ax.axhline(1.0, linestyle="--", label="saturazione")
            finish_axis(ax, "Server", "Utilizzazione teorica", f"{scenario} - {label}")
            ax.set_ylim(0, 1.18)
            ax.legend(fontsize=7)
    fig.suptitle("Utilizzazioni teoriche a λ = 1.40 req/s prima e dopo l'upgrade di B")
    fig.tight_layout()
    save_figure(fig, out_dir, "25_capacity_planning_utilization_heavy", dpi)
    return PlotRecord("25_capacity_planning_utilization_heavy", "U_A/U_B/U_P a λ=1.4 prima/dopo upgrade", "analisi what-if", "core")


def plot_capacity_response_curves(csv_dir: Path, out_dir: Path, dpi: int) -> PlotRecord:
    lam = np.linspace(0.50, 1.405, 450)
    fig, axes = plt.subplots(1, 2, figsize=(13.0, 4.9), sharey=False)
    for ax, scenario in zip(axes, ("1FA", "2FA")):
        rold = theory_response(lam, DEMANDS[scenario])
        rnew = theory_response(lam, DEMANDS_UPGRADED_B[scenario])
        ax.plot(lam, rold, label="Attuale")
        ax.plot(lam, rnew, label="B potenziato (D_B=0.4 s)")
        ax.axvline(throughput_bound(DEMANDS[scenario]), linestyle="--", label="Xmax attuale")
        ax.axvline(throughput_bound(DEMANDS_UPGRADED_B[scenario]), linestyle=":", label="Xmax dopo upgrade")
        ax.axvline(LAMBDA_HEAVY, linestyle="-.", label="λ heavy")
        finish_axis(ax, "λ [req/s]", "R teorico [s]", scenario)
        # Limite grafico robusto: enfatizza la regione operativa senza far dominare la singolarità
        finite = np.concatenate([rold[np.isfinite(rold)], rnew[np.isfinite(rnew)]])
        if finite.size:
            ax.set_ylim(0, min(np.quantile(finite, 0.97) * 1.15, 180))
        ax.legend(fontsize=7)
    fig.suptitle("Capacity planning: effetto analitico del raddoppio della velocità di B")
    fig.tight_layout()
    save_figure(fig, out_dir, "26_capacity_planning_response_curves", dpi)
    return PlotRecord("26_capacity_planning_response_curves", "Curve R teoriche prima/dopo upgrade B", "analisi what-if", "core")


# =============================================================================
# MAIN
# =============================================================================

def main() -> int:
    args = parse_args()
    csv_dir = Path(args.csv_dir)
    out_dir = Path(args.out_dir)

    if not csv_dir.exists():
        print(f"ERRORE: directory CSV non trovata: {csv_dir.resolve()}", file=sys.stderr)
        return 2

    out_dir.mkdir(parents=True, exist_ok=True)

    jobs: list[tuple[Callable[[Path, Path, int], PlotRecord], str]] = [
        (plot_service_demands, "00 service demands"),
        (plot_theoretical_utilization, "01 theoretical utilizations"),
        (plot_validation_response, "02 validation response"),
        (plot_validation_population, "03 validation population"),
        (plot_validation_server_metrics, "04 validation U/X"),
        (plot_little_law, "05 Little law"),
        (plot_convergence_low_load, "06a convergence low load"),
        (plot_convergence_critical, "06b convergence critical"),
        (plot_convergence_window_means, "06c convergence window means"),
        (plot_convergence_final_vs_theory, "06d convergence final vs theory"),
        (plot_transient_replications, "06e transient NB replications"),
        (plot_transient_running_means, "06f transient NB running means"),
        (plot_welch, "06g Welch transient"),
        (plot_acf, "07 ACF"),
        (plot_batch_diagnostic, "08 batch diagnostic"),
        (plot_batch_means_series, "09 batch means series"),
        (plot_regime_response, "10 regime R"),
        (plot_regime_populations, "11 regime N"),
        (plot_regime_utilizations, "12 regime U"),
        (plot_regime_throughputs, "13 regime X"),
        (plot_exp_hyper_aggregate, "14 Exp/H2 aggregate"),
        (plot_exp_hyper_hist, "15 Exp/H2 histogram"),
        (plot_exp_hyper_cdf_ccdf, "16 Exp/H2 CDF"),
        (plot_exp_hyper_percentiles, "17 Exp/H2 percentiles"),
        (plot_job_trace_decomposition, "18 job trace decomposition"),
        (plot_job_trace_percentiles, "19 job trace percentiles"),
        (plot_heavy_populations, "20 heavy populations"),
        (plot_heavy_bottleneck, "21 heavy bottleneck"),
        (plot_heavy_exp_hyper, "22 heavy Exp/H2"),
        (plot_heavy_final_comparison, "23 heavy summary"),
        (plot_capacity_bounds, "24 capacity bounds"),
        (plot_capacity_utilization_heavy, "25 capacity utilization"),
        (plot_capacity_response_curves, "26 capacity response curves"),
    ]

    records: list[PlotRecord] = []
    skipped: list[tuple[str, str]] = []

    print(f"CSV:   {csv_dir.resolve()}")
    print(f"PLOTS: {out_dir.resolve()}\n")

    for func, label in jobs:
        try:
            rec = func(csv_dir, out_dir, args.dpi)
            records.append(rec)
            print(f"[OK]   {rec.stem}")
        except MissingInput as e:
            skipped.append((label, str(e)))
            print(f"[SKIP] {label}: {e}")
        except Exception as e:
            skipped.append((label, f"{type(e).__name__}: {e}"))
            print(f"[ERR]  {label}: {type(e).__name__}: {e}")

    try:
        seed_table = export_convergence_seed_table(csv_dir, out_dir)
        print(f"[OK]   {seed_table.name}")
    except Exception as e:
        print(f"[SKIP] convergence seed table: {type(e).__name__}: {e}")

    manifest = pd.DataFrame([r.__dict__ for r in records])
    manifest.to_csv(out_dir / "plots_manifest.csv", index=False)

    print("\n========================================")
    print(f"Grafici generati: {len(records)}")
    print(f"Grafici saltati:  {len(skipped)}")
    print("========================================")
    if skipped:
        for label, reason in skipped:
            print(f"- {label}: {reason}")
    print(f"\nManifest: {(out_dir / 'plots_manifest.csv').resolve()}")
    print("Ogni figura è disponibile sia in PNG sia in PDF.")
    return 0 if not skipped else 1


if __name__ == "__main__":
    raise SystemExit(main())