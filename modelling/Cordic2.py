from Cordic import Cordic
from math import pi, abs
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

    def cordic(self, angle_indx = 0):
        if angle_indx == 0:
            angle = int(1.790 * (2**15 / 180))
        else:
            angle = int(0.895 * (2**15 / 180))
        # We run two iterations of traditional CORDIC in the same function
        # stage 4:
        input_angle_negative = self.z < 0

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
        # We wish to implement operations for x and y that result to the desired C +jS operation on a complex number but with hardware semantics.
        # Our options are:
        # P_0 = 25 + j0
        # P_1 = 24 + j7
        # P_2 = 20 + j15

        # kernel rotation select
        goal_max_magnitude = int(10.305 * (2**15 / 180))
        abs_z = abs(self.z)
        
        if abs_z <= goal_max_magnitude:
            rotation_idx = 0
        elif abs_z <= self.stage_2_rotations[1] + goal_max_magnitude:
            rotation_idx = 1
        else:
            rotation_idx = 2
        
        input_angle_negative = self.z < 0

        # Let's compute Cx, Sy, Sx, Cy with hardware semantics. 
        if rotation_idx == 0: # P_0 = 25 + j0
            Cx = (self.x << 4) + (self.x << 3) + self.x # 25x = 16x + 8x + x
            Cy = (self.y << 4) + (self.y << 3) + self.y # 25y = 16y + 8y + y
            Sx = 0
            Sy = 0
        elif rotation_idx == 1: # P_1 = 24 + j7
            Cx = (self.x << 4) + (self.x << 3) # 24x = 16x + 8x
            Cy = (self.y << 4) + (self.y << 3) # 24y = 16y + 8y
            Sx = (self.x << 2) + (self.x << 1) + self.x # 7x = 4x + 2x + x
            Sy = (self.y << 2) + (self.y << 1) + self.y # 7y = 4y + 2y + y
        else: # P_2 = 20 + j15
            Cx = (self.x << 4) + (self.x << 2) # 20x = 16x + 4x
            Cy = (self.y << 4) + (self.y << 2) # 20y = 16y + 4y
            Sx = (self.x << 4) + (self.x << 2) + (self.x << 1) + self.x # 15x = 16x - x = 16x - 1x
            Sy = (self.y << 4) + (self.y << 2) + (self.y << 1) + self.y # 15y = 16y - y = 16y - 1y

        if not input_angle_negative: # rotate counterclockwise
            self.x = Cx + Sy
            self.y = Cy - Sx
            self.z += self.stage_2_rotations[rotation_idx]
        else: # rotate clockwise
            self.x = Cx - Sy
            self.y = Cy + Sx
            self.z -= self.stage_2_rotations[rotation_idx]
        """
        X_sum = self.x << 1
        if rotation_idx != 0:
            if not input_angle_negative:
                X_sum -= self.y << 1
            else:
                X_sum += self.y << 1
        else:
            if not input_angle_negative:
                X_sum -= self.x
            else:
                X_sum += self.x
        
        if rotation_idx == 1:
            X_sum = X_sum << 2
        else:
            X_sum = X_sum << 3

        # left shift either by 2 or 5 branch
        if rotation_idx == 1:
            tmp_x_sum = (self.x << 5) + self.y
        elif rotation_idx == 2:
            tmp_x_sum = (self.x << 2) + self.y
        
        Y_sum = self.y << 4
        if rotation_idx != 0:
            if not input_angle_negative:
                Y_sum -= self.x
            else:
                Y_sum += self.x
        else: # rotation_idx == 0
            if not input_angle_negative:
                Y_sum -= self.y << 3
            else:
                Y_sum += self.y << 3
        
        if rotation_idx == 2:
            Y_sum += tmp_x_sum << 2
        elif rotation_idx == 0:
            Y_sum += self.y
        else: # index 1
            Y_sum += X_sum

        if not input_angle_negative:
            if rotation_idx != 0:
                X_sum = tmp_x_sum - X_sum
            else:
                X_sum = -X_sum + self.x
        else:
            if rotation_idx != 0:
                X_sum = tmp_x_sum + X_sum
            else:
                X_sum = X_sum + self.x
        
        self.x = X_sum
        self.y = Y_sum
        if input_angle_negative:
            self.z += self.stage_2_rotations[rotation_idx]
        else:
            self.z -= self.stage_2_rotations[rotation_idx]

        # select rotation angle and sign based on input angle
        # implementation of the friend angle stage with modelling real hardware behaviour
        """
    def usr_cordic(self):
        # select rotation angle and sign based on input angle
        goal_max_magnitude = int(3.563 * (2**15 / 180))
        abs_z = abs(self.z)
        
        if abs_z <= goal_max_magnitude:
            rotation_idx = 0
        else:
            rotation_idx = 1
        input_angle_negative = self.z < 0



        

    def nano_rotations(self):
        # select rotation angle and sign based on input angle
        pass
    
    def run(self):
        pass