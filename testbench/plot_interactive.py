"""Interactive viewer for the CORDIC-2 analysis data.

Run after `make MODULE=test_spectrum` (or combined run):

    python plot_interactive.py

Opens a matplotlib window with zoom / pan enabled so you can inspect how
close the hardware output is to the ideal cos/sin curve.
"""

import os
import sys

import matplotlib.pyplot as plt
import numpy as np

PLOT_DIR  = os.path.join(os.path.dirname(__file__), "plots")
DATA_FILE = os.path.join(PLOT_DIR, "analysis_data.npz")

if not os.path.exists(DATA_FILE):
    print(f"Data file not found: {DATA_FILE}")
    print("Run the cocotb test first:  make MODULE=test_spectrum")
    sys.exit(1)

d = np.load(DATA_FILE)
angles         = d["angles"]
cos_hw         = d["cos_hw"]
sin_hw         = d["sin_hw"]
ideal_cos      = d["ideal_cos"]
ideal_sin      = d["ideal_sin"]
cos_err_lsb    = d["cos_err_lsb"]
sin_err_lsb    = d["sin_err_lsb"]
freqs_centered = d["freqs_centered"]
mag_full_db    = d["mag_full_db"]
sfdr           = float(d["sfdr"])
thd            = float(d["thd"])
N              = int(d["N"])
N_pts          = int(d["N_pts"])

fig, axes = plt.subplots(1, 3, figsize=(16, 5))

# --- Left: sweep (ideal dashed, hw solid — zoom in to see the gap) ---
axes[0].plot(angles, ideal_cos, "k--", linewidth=0.9, alpha=0.5, label="cos ideal")
axes[0].plot(angles, ideal_sin, "k:",  linewidth=0.9, alpha=0.5, label="sin ideal")
axes[0].plot(angles, cos_hw,           linewidth=1.4,             label="cos hw")
axes[0].plot(angles, sin_hw,           linewidth=1.4,             label="sin hw",
             linestyle="--")
axes[0].set_xlim(-180, 175)
axes[0].set_xlabel("Input angle (degrees)")
axes[0].set_ylabel("Normalised amplitude")
axes[0].set_title("Cos / sin sweep  (zoom to see hw vs ideal gap)")
axes[0].legend(fontsize=8)
axes[0].grid(True, alpha=0.3)

# --- Middle: FFT spectrum (two-sided, centred) ---
if N_pts <= 128:
    axes[1].stem(freqs_centered, mag_full_db,
                 markerfmt="C0o", linefmt="C0-", basefmt="grey")
else:
    axes[1].plot(freqs_centered, mag_full_db, linewidth=0.8, color="C0")
    axes[1].fill_between(freqs_centered, mag_full_db, -100, alpha=0.15, color="C0")
axes[1].axhline(-60, color="red", linestyle="--", linewidth=0.8, label="-60 dB")
axes[1].set_xlim(-N_pts // 2, N_pts // 2)
axes[1].set_ylim(-100, 5)
axes[1].set_xlabel("Frequency bin  (0 = DC, ±1 = fundamental)")
axes[1].set_ylabel("Magnitude (dBFS re fundamental)")
axes[1].set_title(
    f"FFT spectrum  (rectangular, N={N_pts})\nSFDR={sfdr:.1f} dB   THD={thd:.1f} dB"
)
axes[1].legend(fontsize=8)
axes[1].grid(True, alpha=0.3)

# --- Right: error vs angle ---
axes[2].plot(angles, cos_err_lsb, linewidth=1.4, label="cos error")
axes[2].plot(angles, sin_err_lsb, linewidth=1.4, label="sin error", linestyle="--")
axes[2].set_xlim(-180, 175)
axes[2].set_xlabel("Input angle (degrees)")
axes[2].set_ylabel("Absolute error (LSBs)")
axes[2].set_title(f"Error vs angle  (N={N}, Q1.15)")
axes[2].legend()
axes[2].grid(True, alpha=0.3)

fig.suptitle("CORDIC-2 analysis  —  interactive", fontsize=14)
fig.tight_layout()
plt.show()
