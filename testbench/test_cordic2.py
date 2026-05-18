"""
cocotb testbench for TopCordic2.

Drives the generated Verilog and compares each output against the Python
reference model (modelling/Cordic2.py) at the raw fixed-point integer level.
This avoids floating-point tolerance issues and catches bit-exact regressions.

Run via:  make           (from this directory, after Verilog generation)
"""
import sys
import os

# Make the Python model importable without installing it
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "modelling"))

import cocotb
from cocotb.clock import Clock
from cocotb.triggers import RisingEdge

from Cordic2 import Cordic2 as Cordic2Model

# --------------------------------------------------------------------------- #
# Constants matching the hardware (Cordic2Constants.scala, N=16, Q1.15)
# --------------------------------------------------------------------------- #
N             = 16
SCALE         = 1 << (N - 1)   # 32768
PIPELINE_GAIN = 1.576           # product of all stage kernel magnitudes
SCALED_X      = int(SCALE / PIPELINE_GAIN)   # pre-scaled so output ≈ 1.0
MAX_LSB_ERROR = 5               # tolerated difference in LSBs between hw and model

TEST_ANGLES = [
    0, 10, 20, 30, 45, 60, 75, 90,
    120, 135, 150, 170,
    -10, -30, -45, -90, -135, -170,
]

# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #
def to_signed(val: int, bits: int = N) -> int:
    """Convert an unsigned cocotb integer value to a signed Python int."""
    val = int(val) & ((1 << bits) - 1)
    if val & (1 << (bits - 1)):
        val -= (1 << bits)
    return val

def deg_to_fp(deg: float) -> int:
    """Convert degrees to Q1.15 fixed-point, masked to N bits."""
    return int(deg * SCALE / 180) & 0xFFFF

# --------------------------------------------------------------------------- #
# Test
# --------------------------------------------------------------------------- #
@cocotb.test()
async def test_cordic2_vs_model(dut):
    """Compare TopCordic2 hardware output against the Python model for all test angles."""

    # Start a 10 ns clock
    cocotb.start_soon(Clock(dut.clock, 10, units="ns").start())

    # Reset for 2 cycles
    dut.reset.value       = 1
    dut.input_valid.value = 0
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)
    dut.reset.value = 0

    print()
    print("=" * 88)
    print(f"cocotb CORDIC-2 — hardware vs Python model  "
          f"(N={N}, Q1.15, scaled_x={SCALED_X})")
    print("=" * 88)

    failures = []

    for angle_deg in TEST_ANGLES:
        # ------------------------------------------------------------------ #
        # Python model reference (uses same integer arithmetic as hardware)
        # ------------------------------------------------------------------ #
        model = Cordic2Model(SCALED_X, 0, angle_deg, input_type=0)
        model.run_optimized()
        mx, my, _ = model.get_result()
        mx = to_signed(mx)   # model already returns Python ints, but sign-extend
        my = to_signed(my)   # for safety when values are near the int16 boundary

        # ------------------------------------------------------------------ #
        # Drive DUT: one cycle of valid input
        # ------------------------------------------------------------------ #
        dut.input_valid.value  = 1
        dut.input_bits_x.value = SCALED_X & 0xFFFF
        dut.input_bits_y.value = 0
        dut.input_bits_z.value = deg_to_fp(angle_deg)
        await RisingEdge(dut.clock)
        dut.input_valid.value  = 0

        # ------------------------------------------------------------------ #
        # Poll for output_valid (up to 120 cycles)
        # ------------------------------------------------------------------ #
        for _ in range(120):
            if int(dut.output_valid.value) == 1:
                break
            await RisingEdge(dut.clock)
        else:
            assert False, f"Timeout waiting for output at {angle_deg}°"

        # ------------------------------------------------------------------ #
        # Read and compare
        # ------------------------------------------------------------------ #
        hw_x = to_signed(int(dut.output_bits_x.value))
        hw_y = to_signed(int(dut.output_bits_y.value))

        err_x = abs(hw_x - mx)
        err_y = abs(hw_y - my)
        ok    = err_x <= MAX_LSB_ERROR and err_y <= MAX_LSB_ERROR

        status = "PASS" if ok else "FAIL"
        print(f"  [{status}] {angle_deg:+7.1f}° | "
              f"hw=({hw_x:6d},{hw_y:6d})  model=({mx:6d},{my:6d})  "
              f"err_x={err_x}  err_y={err_y}")

        if not ok:
            failures.append(
                f"{angle_deg}°: hw=({hw_x},{hw_y}) model=({mx},{my}) "
                f"err=({err_x},{err_y})"
            )

        await RisingEdge(dut.clock)  # step past valid cycle before next input

    print("=" * 88)
    assert not failures, "The following angles exceeded the error threshold:\n" + \
                         "\n".join(failures)
    print(f"All {len(TEST_ANGLES)} angles passed (≤ {MAX_LSB_ERROR} LSB error).")
