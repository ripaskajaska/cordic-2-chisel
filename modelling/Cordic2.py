from Cordic import Cordic
from math import pi
class Cordic2:

    stage_1_rotations = [0, 2**14, 2**15]
    stage_2_rotations = [0, int(16.260 * (2**15 / 180)), 36.870 * (2**15 / 180)]
    stage_3_rotations = [0, int(7.125 * (2**15 / 180))]
    stage_4_rotation = int(1.790 * (2**15 / 180))
    stage_5_rotation = int(0.895 * (2**15 / 180))
    stage_6_rotations = [ i * int(0.112 * (2**15 / 180)) for i in range (0, 8) ]
    stage_6_bis_rotation = int(0.448 * (2**15 / 180))
    stage_7_bis_rotations = [ i * int(0.056 * (2**15 / 180)) for i in range (0, 8) ]
    # Cordic 2 performs single iteration of each of its stage.
    def __init__(self, x: int, y: int, z: int):
        
        self.x: int = x
        self.y: int = y
        self.z: int = z
        self.quadrant = 0

    def cordic(self):
        pass
        # select rotation angle and sign based on input angle
        
    def trivial_rotations(self):

        # trivial rotations to reduce the input angle to [-45, 45[

        if self.z < 0:
            # 2**13 corresponds to 45 degrees
            # 2**14 corresponds to 90 degrees
            # 2**15 corresponds to 180 degrees
            if -2**13 * 3 <= self.z < -2**13:
                self.x, self.y, self.z = self.y, -self.x, self.z + self.stage_1_rotations[1]
            elif -2**15 <= self.z < -2**13 * 3:
                self.x, self.y, self.z = -self.x, -self.y, self.z + self.stage_1_rotations[2]
            else:
                # No need for changes
                self.x, self.y, self.z = self.x, self.y, self.z
        else:
            if 2**13 < self.z <= 2**13 * 3:
                self.x, self.y, self.z = -self.y, self.x, self.z - self.stage_1_rotations[1]
            elif 2**13 * 3 < self.z <= 2**15:
                self.x, self.y, self.z = -self.x, -self.y, self.z - self.stage_1_rotations[2]
            else:
                self.x, self.y, self.z = self.x, self.y, self.z

    def normalize_angle(self):
        self.z = self.z % 360
        # normalize angle to [-180, 180[
        if self.z > 180:
            self.z -= 360
        if self.z < -180:
            self.z += 360
        if self.z == 180:
            self.z = -180
        # convert to fixed-point representation
        self.z = int(self.z * (2**15 / 180))

    def friend_angles_bit_accurate(self):
        # kernel rotation select
        goal_max_magnitude = int(10.305 * (2**15 / 180))
        angle_negative = self.z < 0
        if angle_negative:
            if self.z < -self.stage_2_rotations[2]:
                rotation_idx = 2
            elif self.z < -(self.stage_2_rotations[1] + goal_max_magnitude):
                rotation_idx = 2
            elif self.z < -self.stage_2_rotations[1]:
                rotation_idx = 1
            else:
                rotation_idx = 0
        else:
            if self.z > self.stage_2_rotations[2]:
                rotation_idx = 2
            elif self.z > self.stage_2_rotations[1] + goal_max_magnitude:
                rotation_idx = 2
            elif self.z > self.stage_2_rotations[1]:
                rotation_idx = 1
            else:
                rotation_idx = 0
        X_sum = self.x << 1
        if rotation_idx != 0:
            if not angle_negative:
                X_sum -= self.y << 1
            else:
                X_sum += self.y << 1
        else:
            if not angle_negative:
                X_sum -= self.x
            else:
                X_sum += self.x
        if rotation_idx == 1:
            X_sum == X_sum << 2
        else:
            X_sum == X_sum << 3

        
        
        Y_sum = self.y << 4
        if rotation_idx != 0:
            if not angle_negative:
                Y_sum -= self.x
            else:
                Y_sum += self.x
        else: # rotation_idx == 0
            if not angle_negative:
                Y_sum -= self.y << 3
            else:
                Y_sum += self.y << 3
        

        if rotation_idx == 1:
            X_sum += self.x << 5 + self.y
        
            



        # select rotation angle and sign based on input angle
        # implementation of the friend angle stage with modelling real hardware behaviour

    def usr_cordic(self):
        # select rotation angle and sign based on input angle
        pass

    def nano_rotations(self):
        # select rotation angle and sign based on input angle
        pass
    
    def run(self):
        pass