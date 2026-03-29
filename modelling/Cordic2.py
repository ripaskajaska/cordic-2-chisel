from Cordic import Cordic
from math import pi
class Cordic2:

    stage_1_rotations = [0, 90, 180, 270]
    stage_2_rotations = [0, 16.260, 36.870]
    stage_3_rotations = [0, 7.125]
    stage_4_rotation = 1.790
    stage_5_rotation = 0.895
    stage_6_rotations = [ i * 0.112 for i in range (0, 8) ]
    stage_6_bis_rotation = 0.448
    stage_7_bis_rotations = [ i * 0.056 for i in range (0, 8) ]
    # Cordic 2 performs single iteration of each of its stage.
    def __init__(self, x: int, y: int, z: int):
        
        self.x: int = x
        self.y: int = y
        self.z: int = z
        self.quadrant = 0

    def cordic(self, x: int, y: int, a_in: int):
        pass
        # select rotation angle and sign based on input angle
        
    def trivial_rotations(self, x: int, y: int, a_in: int):
        # select rotation angle and sign based on input angle
        if a_in < 0:
            if -45 <= a_in:
                self.x, self.y, self.z = x, y, a_in
            elif -135 <= a_in < -45:
                self.x, self.y, self.z = y, -x, a_in + 90
            elif -225 <= a_in < -135:
                self.x, self.y, self.z = -x, -y, a_in + 180
            elif -315 <= a_in < -225:
                self.x, self.y, self.z = -y, x, a_in + 270
            else:
                self.x, self.y, self.z = x, y, a_in
        else:
            if 45 < a_in <= 135:
                self.x, self.y, self.z = -y, x, a_in - 90
            elif 135 < a_in <= 225:
                self.x, self.y, self.z = -x, -y, a_in - 180
            elif 225 < a_in <= 315:
                self.x, self.y, self.z = y, -x, a_in - 270
            else:
                self.x, self.y, self.z = x, y, a_in

    def quadrant_select(self, a_in: int):
        if a_in < 0:
            if -45 <= a_in:
                self.quadrant = 0
            elif -135 <= a_in < -45:
                self.quadrant = 1
            elif -225 <= a_in < -135:
                self.quadrant = 2
            elif -315 <= a_in < -225:
                self.quadrant = 3
            else:
                self.quadrant = 0
        else:
            if 45 < a_in <= 135:
                self.quadrant = 1
            elif 135 < a_in <= 225:
                self.quadrant = 2
            elif 225 < a_in <= 315:
                self.quadrant = 3
            else:
                self.quadrant = 0
    
    def normalize_angle(self, a_in: int):
        if self.quadrant == 0:
            self.z = a_in * (2**15 / 90)
    
    
    def quadrant_correction(self, x: int, y: int):
        if self.quadrant == 0:
            self.x, self.y = x, y
        elif self.quadrant == 1:
            self.x, self.y = -y, x
        elif self.quadrant == 2:
            self.x, self.y = -x, -y
        elif self.quadrant == 3:
            self.x, self.y = y, -x

    def friend_angles(self, x: int, y: int, a_in: int):
        # select rotation angle and sign based on input angle
        pass

    def usr_cordic(self, x: int, y: int, z: int, n: int):
        # select rotation angle and sign based on input angle
        pass

    def nano_rotations(self, x: int, y: int, z: int, n: int):
        # select rotation angle and sign based on input angle
        pass
    
    def run(self):
        pass