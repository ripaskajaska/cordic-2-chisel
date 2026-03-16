import random

def main():
    integer = get_random_integer(-2**16, 2**16 - 1)

def get_random_integer(min_value: int, max_value: int) -> int:
    return random.randint(min_value, max_value)

