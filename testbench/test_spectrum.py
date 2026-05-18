"""
cocotb spectral analysis for TopCordic2.

Produces testbench/plots/analysis.png — a single 1×3 figure:
  [left]   cos and sin sweep (hw vs ideal)
  [middle] FFT magnitude spectrum of the cosine output
  [right]  absolute error vs angle for cos and sin

All three panels share one 72-point sweep (-180° to +175° in 5° steps),
so the hardware is driven only once.

Run with:
  make MODULE=test_spectrum
  make MODULE=test_cordic2,test_spectrum   # both suites together
"""
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "modelling"))

import numpy as np
import matplotlib
matplotlib.use("Agg")           # headless — no display required
import matplotlib.pyplot as plt

import cocotb
from cocotb.clock import Clock
from cocotb.triggers import RisingEdge

# --------------------------------------------------------------------------- #
# Constants
# --------------------------------------------------------------------------- #
N             = 16
SCALE         = 1 << (N - 1)   # 32768
PIPELINE_GAIN = 1.576
SCALED_X      = int(SCALE / PIPELINE_GAIN)

PLOT_DIR = os.path.join(os.path.dirname(__file__), "plots")

# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #
def to_signed(val: int, bits: int = N) -> int:
    val = int(val) & ((1 << bits) - 1)
    if val & (1 << (bits - 1)):
        val -= (1 << bits)
    return val

def deg_to_fp(deg: float) -> int:
    return int(deg * SCALE / 180) & 0xFFFF

async def reset_dut(dut) -> None:
    dut.reset.value       = 1
    dut.input_valid.value = 0
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)
    dut.reset.value = 0

async def run_angle(dut, angle_deg: float) -> tuple[int, int]:
    """Drive one angle through the full pipeline; return (x_out, y_out) as signed ints."""
    dut.input_valid.value  = 1
    dut.input_bits_x.value = SCALED_X & 0xFFFF
    dut.input_bits_y.value = 0
    dut.input_bits_z.value = deg_to_fp(angle_deg)
    await RisingEdge(dut.clock)
    dut.input_valid.value  = 0

    for _ in range(120):
        if int(dut.output_valid.value) == 1:
            break
        await RisingEdge(dut.clock)
    else:
        raise AssertionError(f"Timeout waiting for output at {angle_deg}°")

    x = to_signed(int(dut.output_bits_x.value))
    y = to_signed(int(dut.output_bits_y.value))
    await RisingEdge(dut.clock)     # step past the valid cycle before next input
    return x, y

# --------------------------------------------------------------------------- #
# Test — single pass: sweep, FFT, error in one 1×3 figure
# --------------------------------------------------------------------------- #
@cocotb.test()
async def test_sweep_and_spectrum(dut):
    """
    72-point sweep (-180° to +175°, 5° steps) drives the hardware once.
    Results are plotted as a 1×3 figure:
      [left]   cos / sin sweep  (hw vs ideal)
      [middle] FFT spectrum of the cosine output  (harmonic distortion)
      [right]  absolute error vs angle  (LSBs)
    """
    cocotb.start_soon(Clock(dut.clock, 10, units="ns").start())
    await reset_dut(dut)

    # Uniformly-spaced angles covering exactly one full period.
    # More points = smoother sweep and error curves + better FFT frequency resolution.
    # 1000 points ≈ 0.36° step. FFT bin 1 is always the fundamental regardless of N_pts.
    N_pts  = 1000
    angles = np.linspace(-180, 180, N_pts, endpoint=False)

    cos_hw = np.empty(N_pts)
    sin_hw = np.empty(N_pts)

    for i, ang in enumerate(angles):
        x, y      = await run_angle(dut, ang)
        cos_hw[i] = x / SCALE
        sin_hw[i] = y / SCALE

    # ------------------------------------------------------------------ #
    # Derived quantities
    # ------------------------------------------------------------------ #
    ideal_cos   = np.cos(np.radians(angles))
    ideal_sin   = np.sin(np.radians(angles))
    cos_err_lsb = np.abs(cos_hw - ideal_cos) * SCALE
    sin_err_lsb = np.abs(sin_hw - ideal_sin) * SCALE

    # No window: the sweep covers exactly one full period (N_pts × 360°/N_pts = 360°),
    # so the signal sits at exactly bin 1 of the DFT. For integer-bin signals a
    # rectangular window (ones) produces zero spectral leakage. A Hann window would
    # introduce a deterministic −6 dB artifact at bin 2 regardless of hardware quality.
    window      = np.ones(N_pts)
    spectrum    = np.fft.rfft(cos_hw * window)
    mag         = np.abs(spectrum)
    fundamental = mag[1]

    # Two-sided shifted spectrum for display: DC at centre, fundamental at ±1
    spectrum_full   = np.fft.fftshift(np.fft.fft(cos_hw * window))
    mag_full        = np.abs(spectrum_full)
    mag_full_db     = 20.0 * np.log10(mag_full / fundamental + 1e-12)
    freqs_centered  = np.arange(-N_pts // 2, N_pts - N_pts // 2)   # −N/2 … +N/2−1

    harmonics = mag[2 : N_pts // 2]
    sfdr = -20.0 * np.log10(harmonics.max()                 / fundamental + 1e-12)
    thd  =  20.0 * np.log10(np.sqrt((harmonics**2).sum())   / fundamental + 1e-12)

    # ------------------------------------------------------------------ #
    # 1×3 figure
    # ------------------------------------------------------------------ #
    fig, axes = plt.subplots(1, 3, figsize=(16, 5))

    # --- Left: sweep ---
    axes[0].plot(angles, ideal_cos, "k--", linewidth=0.9, alpha=0.45, label="cos ideal")
    axes[0].plot(angles, ideal_sin, "k:" , linewidth=0.9, alpha=0.45, label="sin ideal")
    axes[0].plot(angles, cos_hw,           linewidth=1.4,              label="cos hw")
    axes[0].plot(angles, sin_hw,           linewidth=1.4,              label="sin hw",
                 linestyle="--")
    axes[0].set_xlim(-180, 175)
    axes[0].set_xlabel("Input angle (degrees)")
    axes[0].set_ylabel("Normalised amplitude")
    axes[0].set_title("Cos / sin sweep")
    axes[0].legend(fontsize=8)
    axes[0].grid(True, alpha=0.3)

    # --- Middle: FFT spectrum (two-sided, centred on DC; fundamental at bins ±1) ---
    if N_pts <= 128:
        axes[1].stem(freqs_centered, mag_full_db, markerfmt="C0o", linefmt="C0-", basefmt="grey")
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

    # --- Right: error ---
    axes[2].plot(angles, cos_err_lsb, linewidth=1.4, label="cos error")
    axes[2].plot(angles, sin_err_lsb, linewidth=1.4, label="sin error", linestyle="--")
    axes[2].set_xlim(-180, 175)
    axes[2].set_xlabel("Input angle (degrees)")
    axes[2].set_ylabel("Absolute error (LSBs)")
    axes[2].set_title(f"Error vs angle  (N={N}, Q1.15)")
    axes[2].legend()
    axes[2].grid(True, alpha=0.3)

    fig.suptitle("CORDIC-2 analysis", fontsize=14)
    fig.tight_layout()

    os.makedirs(PLOT_DIR, exist_ok=True)
    path = os.path.join(PLOT_DIR, "analysis.png")
    fig.savefig(path, dpi=150, bbox_inches="tight")
    plt.close(fig)

    print(f"\nPlot saved → {path}")
    print(f"  max cos error = {cos_err_lsb.max():.2f} LSBs  "
          f"(at {angles[cos_err_lsb.argmax()]:.0f}°)")
    print(f"  max sin error = {sin_err_lsb.max():.2f} LSBs  "
          f"(at {angles[sin_err_lsb.argmax()]:.0f}°)")
    print(f"  SFDR = {sfdr:.1f} dB")
    print(f"  THD  = {thd:.1f} dB")
