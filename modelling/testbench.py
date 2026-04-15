import random
import numpy as np
import matplotlib.pyplot as plt
from math import cos, sin, radians
from Cordic2 import Cordic2

def random_test(k: float, fractional_bits: int):
    angle_deg = get_random_angle()
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

def plot_sine(k: float, fractional_bits: int):
    x = float_to_fxp(k, fractional_bits)
    angles = np.linspace(-360, 360, 1000)
    ref_sines = np.sin(np.radians(angles))
    cordic_sines = []
    for angle in angles:
        # Simulate CORDIC sine calculation (replace with actual CORDIC implementation)
        cordic2 = Cordic2(x, 0, angle, 0)
        cordic2.run()
        out_x, out_y, out_z = cordic2.get_result()
        y_normalized = fxp_to_float(out_y, fractional_bits)
        cordic_sines.append(y_normalized)
    
    cordic_sines = np.array(cordic_sines) 
    plt.figure(figsize=(10, 5))
    plt.plot(angles, ref_sines, label='Reference Sine Wave', color='blue')
    plt.plot(angles, cordic_sines, label='CORDIC2 Sine Wave', color='red')
    plt.title('Sine Function')
    plt.xlabel('Angle (degrees)')
    plt.ylabel('Sine Value')
    plt.grid()
    plt.legend()
    plt.xlim(-360, 360)
    plt.ylim(-1.5, 1.5)
    plt.show()

def plot_sine_and_cosine(k: float, fractional_bits: int):
    x = float_to_fxp(k, fractional_bits)
    angles = np.linspace(-360, 360, 1000)
    ref_cosines = np.cos(np.radians(angles))
    ref_sines = np.sin(np.radians(angles))
    cordic_cosines = []
    cordic_sines = []
    for angle in angles:
        # Simulate CORDIC cosine calculation (replace with actual CORDIC implementation)
        cordic2 = Cordic2(x, 0, angle, 0)
        cordic2.run()
        out_x, out_y, out_z = cordic2.get_result()
        x_normalized = fxp_to_float(out_x, fractional_bits)
        y_normalized = fxp_to_float(out_y, fractional_bits)
        cordic_cosines.append(x_normalized)
        cordic_sines.append(y_normalized)

    cordic_cosines = np.array(cordic_cosines)
    cordic_sines = np.array(cordic_sines)
    plt.figure(figsize=(10, 5))
    plt.plot(angles, ref_cosines, label='Reference Cosine Wave', color='orange')
    plt.plot(angles, cordic_cosines, label='CORDIC2 Cosine Wave', color='red')
    plt.plot(angles, ref_sines, label='Reference Sine Wave', color='blue')
    plt.plot(angles, cordic_sines, label='CORDIC2 Sine Wave', color='green')
    plt.title('Trigonometric Functions')
    plt.xlabel('Angle (degrees)')
    plt.ylabel('Value')
    plt.grid()
    plt.legend()
    plt.xlim(-360, 360)
    plt.ylim(-1.5, 1.5)
    plt.show()