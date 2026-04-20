
from testbench import *
FIXED_POINT_BITS = 16
def main():
    total_approximation_error = 1.5625
    k = 1.0 / total_approximation_error
    random_test(k, FIXED_POINT_BITS)
    compare_optimized_and_non(k, FIXED_POINT_BITS)
    average_max_error_calculation(k, FIXED_POINT_BITS, 1000)
    plot_sine_and_cosine(k, FIXED_POINT_BITS)

if __name__ == "__main__":
    main()