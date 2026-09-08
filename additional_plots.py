#!/usr/bin/env python3
"""
additional_plots.py

Grafici per la relazione:
  27_validation_relative_error
  28_2FA_response_time_penalty
  29_regime_relative_CI_width

Esecuzione dalla root del progetto:

    python3 additional_plots.py

Input:
    csv/validation_scan.csv
    csv/regime_experiment.csv

Output:
    plots_extra/*.png
    plots_extra/*.pdf
"""

from pathlib import Path

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt


ROOT = Path(__file__).resolve().parent
CSV_DIR = ROOT / "csv"
OUT_DIR = ROOT / "plots_extra"

VALIDATION_CSV = CSV_DIR / "validation_scan.csv"
REGIME_CSV = CSV_DIR / "regime_experiment.csv"

OUT_DIR.mkdir(parents=True, exist_ok=True)

DEMANDS = {
    "1FA": {"A": 0.70, "B": 0.80, "P": 0.40},
    "2FA": {"A": 0.75, "B": 0.80, "P": 0.70},
}


def require_file(path: Path) -> None:
    if not path.exists():
        raise FileNotFoundError(
            f"File richiesto non trovato:\n  {path}\n"
            "Verifica di eseguire lo script dalla root di PMCSN "
            "e che i CSV siano nella cartella csv/."
        )


def save_figure(fig, basename: str) -> None:
    png_path = OUT_DIR / f"{basename}.png"
    pdf_path = OUT_DIR / f"{basename}.pdf"

    fig.savefig(png_path, dpi=300, bbox_inches="tight")
    fig.savefig(pdf_path, bbox_inches="tight")
    plt.close(fig)

    print(f"[OK] {png_path}")
    print(f"[OK] {pdf_path}")


def theory_response_time(lmbda: np.ndarray, scenario: str) -> np.ndarray:
    d = DEMANDS[scenario]
    result = np.zeros_like(lmbda, dtype=float)

    for center in ("A", "B", "P"):
        result += d[center] / (1.0 - lmbda * d[center])

    return result


def theory_population(lmbda: np.ndarray, scenario: str) -> np.ndarray:
    return lmbda * theory_response_time(lmbda, scenario)


def relative_error_percent(sim: np.ndarray, theory: np.ndarray) -> np.ndarray:
    return 100.0 * (sim - theory) / theory


def scenario_frame(df: pd.DataFrame, scenario: str) -> pd.DataFrame:
    return (
        df[df["scenario"] == scenario]
        .copy()
        .sort_values("lambda")
        .reset_index(drop=True)
    )


def plot_validation_relative_error(validation: pd.DataFrame) -> None:
    fig, axes = plt.subplots(2, 1, figsize=(9.5, 8.0), sharex=True)

    for scenario in ("1FA", "2FA"):
        df = scenario_frame(validation, scenario)
        lmbda = df["lambda"].to_numpy(dtype=float)

        r_sim = df["R"].to_numpy(dtype=float)
        n_sim = df["N_tot"].to_numpy(dtype=float)

        r_theory = theory_response_time(lmbda, scenario)
        n_theory = theory_population(lmbda, scenario)

        axes[0].plot(
            lmbda,
            relative_error_percent(r_sim, r_theory),
            marker="o",
            linewidth=1.8,
            markersize=4.5,
            label=scenario,
        )

        axes[1].plot(
            lmbda,
            relative_error_percent(n_sim, n_theory),
            marker="o",
            linewidth=1.8,
            markersize=4.5,
            label=scenario,
        )

    axes[0].axhline(0.0, linewidth=1.0, linestyle="--")
    axes[1].axhline(0.0, linewidth=1.0, linestyle="--")

    axes[0].set_ylabel("Errore relativo di R [%]")
    axes[1].set_ylabel("Errore relativo di N [%]")
    axes[1].set_xlabel(r"$\lambda$ [req/s]")

    axes[0].set_title(
        "Validazione: errore relativo rispetto al modello analitico"
    )

    for ax in axes:
        ax.grid(True, alpha=0.30)
        ax.legend()
        ax.margins(x=0.02)

    fig.tight_layout()
    save_figure(fig, "27_validation_relative_error")


def plot_2fa_response_time_penalty(regime: pd.DataFrame) -> None:
    one = scenario_frame(regime, "1FA")
    two = scenario_frame(regime, "2FA")

    l1 = one["lambda"].to_numpy(dtype=float)
    l2 = two["lambda"].to_numpy(dtype=float)

    if not np.allclose(l1, l2):
        raise ValueError("I valori di lambda per 1FA e 2FA non coincidono.")

    r1 = one["R_mean"].to_numpy(dtype=float)
    r2 = two["R_mean"].to_numpy(dtype=float)

    delta_abs = r2 - r1
    delta_pct = 100.0 * delta_abs / r1

    fig, axes = plt.subplots(2, 1, figsize=(9.5, 8.0), sharex=True)

    axes[0].plot(l1, delta_abs, marker="o", linewidth=2.0, markersize=5)
    axes[1].plot(l1, delta_pct, marker="o", linewidth=2.0, markersize=5)

    axes[0].set_ylabel(r"$\Delta R$ [s]")
    axes[1].set_ylabel(r"Penalità 2FA su $R$ [%]")
    axes[1].set_xlabel(r"$\lambda$ [req/s]")

    axes[0].set_title(
        "Impatto incrementale della 2FA sul tempo di risposta"
    )

    for ax in axes:
        ax.axhline(0.0, linewidth=1.0, linestyle="--")
        ax.grid(True, alpha=0.30)
        ax.margins(x=0.02)

    idx = int(np.argmax(l1))

    axes[0].annotate(
        f"{delta_abs[idx]:.2f} s",
        xy=(l1[idx], delta_abs[idx]),
        xytext=(-55, 15),
        textcoords="offset points",
        arrowprops={"arrowstyle": "->"},
    )

    axes[1].annotate(
        f"{delta_pct[idx]:.1f}%",
        xy=(l1[idx], delta_pct[idx]),
        xytext=(-55, 15),
        textcoords="offset points",
        arrowprops={"arrowstyle": "->"},
    )

    fig.tight_layout()
    save_figure(fig, "28_2FA_response_time_penalty")


def plot_regime_relative_ci_width(regime: pd.DataFrame) -> None:
    fig, axes = plt.subplots(2, 1, figsize=(9.5, 8.0), sharex=True)

    for scenario in ("1FA", "2FA"):
        df = scenario_frame(regime, scenario)
        lmbda = df["lambda"].to_numpy(dtype=float)

        r_mean = df["R_mean"].to_numpy(dtype=float)
        r_hw = df["R_hw"].to_numpy(dtype=float)

        nb_mean = df["N_B_mean"].to_numpy(dtype=float)
        nb_hw = df["N_B_hw"].to_numpy(dtype=float)

        rel_r = 100.0 * r_hw / r_mean
        rel_nb = 100.0 * nb_hw / nb_mean

        axes[0].plot(
            lmbda,
            rel_r,
            marker="o",
            linewidth=1.8,
            markersize=4.5,
            label=scenario,
        )

        axes[1].plot(
            lmbda,
            rel_nb,
            marker="o",
            linewidth=1.8,
            markersize=4.5,
            label=scenario,
        )

    axes[0].set_ylabel("Half-width relativo di R [%]")
    axes[1].set_ylabel(r"Half-width relativo di $N_B$ [%]")
    axes[1].set_xlabel(r"$\lambda$ [req/s]")

    axes[0].set_title(
        "Precisione statistica degli IC 95% al crescere del carico"
    )

    for ax in axes:
        ax.grid(True, alpha=0.30)
        ax.legend()
        ax.margins(x=0.02)

    fig.tight_layout()
    save_figure(fig, "29_regime_relative_CI_width")


def main() -> None:
    require_file(VALIDATION_CSV)
    require_file(REGIME_CSV)

    validation = pd.read_csv(VALIDATION_CSV)
    regime = pd.read_csv(REGIME_CSV)

    required_validation = {"scenario", "lambda", "R", "N_tot"}
    required_regime = {
        "scenario",
        "lambda",
        "R_mean",
        "R_hw",
        "N_B_mean",
        "N_B_hw",
    }

    missing_validation = required_validation - set(validation.columns)
    missing_regime = required_regime - set(regime.columns)

    if missing_validation:
        raise ValueError(
            "Colonne mancanti in validation_scan.csv: "
            + ", ".join(sorted(missing_validation))
        )

    if missing_regime:
        raise ValueError(
            "Colonne mancanti in regime_experiment.csv: "
            + ", ".join(sorted(missing_regime))
        )

    print("========================================")
    print("PMCSN - Grafici aggiuntivi")
    print("========================================")
    print(f"Input validation: {VALIDATION_CSV}")
    print(f"Input regime:     {REGIME_CSV}")
    print(f"Output:           {OUT_DIR}")
    print()

    plot_validation_relative_error(validation)
    plot_2fa_response_time_penalty(regime)
    plot_regime_relative_ci_width(regime)

    print()
    print("========================================")
    print("Completato: 3 figure generate.")
    print("========================================")


if __name__ == "__main__":
    main()
