
from testbench import *
FIXED_POINT_BITS = 16
def main():
    total_approximation_error = 1.5625
    k = 1.0 / total_approximation_error
    random_test(k, FIXED_POINT_BITS)
    plot_sine_and_cosine(k, FIXED_POINT_BITS)

if __name__ == "__main__":
    main()