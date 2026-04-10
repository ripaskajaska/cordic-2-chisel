import random
from math import cos, sin, radians
from Cordic2 import Cordic2
def random_test(fractional_bits: int):
    angle_deg = get_random_angle()
    total_approximation_error = 1.5625
    k = 1.0 / total_approximation_error
    x = float_to_fxp(k, fractional_bits)

    # Convert angle to fixed-point representation (bits 15 for angle encoding)
    angle_fp = int(angle_deg * (2**15 / 180))

    print(f"Random angle: {angle_deg}°")
    cordic2 = Cordic2(x, 0, angle_deg, 0)
    cordic2.run()
    out_x, out_y, out_z = cordic2.get_result()

    cosine = cos(radians(angle_deg))
    sine = sin(radians(angle_deg))

    x_normalized = fxp_to_float(out_x, fractional_bits)
    y_normalized = fxp_to_float(out_y, fractional_bits)

    print(f"CORDIC2 output (raw): x={out_x}, y={out_y}, z={out_z}")
    print(f"CORDIC2 output (normalized): x={x_normalized}, y={y_normalized}")
    print(f"Expected cosine: {cosine}, sine: {sine}")
    print(f"Error in cosine: {abs(x_normalized - cosine)}, Error in sine: {abs(y_normalized - sine)}")

def get_random_angle() -> float:
    return random.uniform(-360, 360)

def fxp_to_float(fxp_value: int, fractional_bits: int) -> float:
    return fxp_value / (2 ** fractional_bits)

def float_to_fxp(value: float, fractional_bits: int) -> int:
    return int(value * (2 ** fractional_bits))