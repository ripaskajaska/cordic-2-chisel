from statistics import mode
from math import atan

class Cordic:
    def __init__(self, x: int, y: int, z: int, iterations: int, k: float = 1.0, mode: int = 0):
        self.iteration: int = iterations
        self.x = x
        self.y = y
        self.z = z
        self.iteration = iterations
        self.K = k
        self.atan_table = [0.0] * iterations
        self.mode = mode # template, used for different operation modes
        self.generate_atan_table()

    
    def generate_atan_table(self):
        for i in range(self.iterations):
            self.atan_table[i] = int((1 << 16) * atan(1 / (1 << i)))  # Fixed-point representation


    def run(self): # get rotation for iteration i
        if self.mode == 0:
            for i in range(self.iteration):
                if self.z < 0:
                    x_new = self.x + (self.y >> i)
                    y_new = self.y - (self.x >> i)
                    z_new = self.z + self.atan_table[i]
                else:
                    x_new = self.x - (self.y >> i)
                    y_new = self.y + (self.x >> i)
                    z_new = self.z - self.atan_table[i]
                self.x, self.y, self.z = x_new, y_new, z_new
        else:
            # Implement other modes
            pass

    def get_x(self) -> int:
        return self.x
    def get_y(self) -> int:
        return self.y
    def get_z(self) -> int:
        return self.z