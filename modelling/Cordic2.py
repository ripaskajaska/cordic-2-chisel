
class Cordic2:

    stage_1_rotations = [0, 2**14, 2**15]
    stage_2_rotations = [0, int(16.260 * (2**15 / 180)), int(36.870 * (2**15 / 180))]
    stage_3_rotations = [0, int(7.125 * (2**15 / 180))]
    stage_4_rotation = int(1.790 * (2**15 / 180))
    stage_5_rotation = int(0.895 * (2**15 / 180))
    stage_6_rotations = [ i * int(0.112 * (2**15 / 180)) for i in range(0, 9) ]
    stage_6_bis_rotation = int(0.448 * (2**15 / 180))
    stage_7_bis_rotations = [ i * int(0.056 * (2**15 / 180)) for i in range(0, 9) ]
    # Cordic 2 performs single iteration of each of its stage.
    def __init__(self, x: int, y: int, angle: float | int, input_type: int = 0):
        # input type 0 corresponds to angle in degrees and x, y in fixed-point representation
        self.input_angle: float = angle
        self.x: int = x
        self.y: int = y
        self.z: int = 0
        if input_type == 0:
            self.normalize_and_convert_angle()
        else:
            self.z = angle

    def get_result(self):
        return self.x, self.y, self.z

    def cordic(self, angle_indx = 0):
        if angle_indx == 0:
            angle = int(1.790 * (2**15 / 180))

        else:
            angle = int(0.895 * (2**15 / 180))

        # We run two iterations of traditional CORDIC in the same function
        # stage 4:
        # Cordidic rotations:
        # On index 0: 32 + j
        # On index 1: 64 + j
        input_angle_negative = self.z < 0
        if angle_indx == 0:
            Cx = (self.x << 5)
            Cy = (self.y << 5)
            Sx = self.x
            Sy = self.y
        else:
            Cx = (self.x << 6)
            Cy = (self.y << 6)
            Sx = self.x
            Sy = self.y
        if input_angle_negative: # rotate counterclockwise
            self.x = Cx + Sy
            self.y = Cy - Sx
            self.z += angle
        else: # rotate clockwise
            self.x = Cx - Sy
            self.y = Cy + Sx
            self.z -= angle

        if angle_indx == 0:
            self.x = self.x >> 5
            self.y = self.y >> 5
        else:
            self.x = self.x >> 6
            self.y = self.y >> 6

    def cordic_optimized(self, angle_indx = 0):
        if angle_indx == 0:
            angle = int(1.790 * (2**15 / 180))

        else:
            angle = int(0.895 * (2**15 / 180))

        # We run two iterations of traditional CORDIC in the same function
        # stage 4:
        # Cordidic rotations:
        # On index 0: 32 + j
        # On index 1: 64 + j

        input_angle_negative = self.z < 0

        if input_angle_negative: # rotate counterclockwise
            self.z += angle
        else: # rotate clockwise
            self.z -= angle

        if angle_indx == 0:
            Cx = (self.x << 5)
            Cy = (self.y << 5)
            Sx = self.x
            Sy = self.y
            self.x = self.adder_subtractor(Cx, Sy, subtract=not input_angle_negative)
            self.y = self.adder_subtractor(Cy, Sx, subtract=input_angle_negative)
            self.x = self.x >> 5
            self.y = self.y >> 5
        else:
            Cx = (self.x << 6)
            Cy = (self.y << 6)
            Sx = self.x
            Sy = self.y
            self.x = self.adder_subtractor(Cx, Sy, subtract=not input_angle_negative)
            self.y = self.adder_subtractor(Cy, Sx, subtract=input_angle_negative)
            self.x = self.x >> 6
            self.y = self.y >> 6

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
            elif 2**13 * 3 < self.z < 2**15:
                self.x, self.y, self.z = -self.x, -self.y, self.z - self.stage_1_rotations[2]
            else:
                self.x, self.y, self.z = self.x, self.y, self.z

    def normalize_and_convert_angle(self):
        self.z = self.input_angle % 360
        # normalize angle to [-180, 180[ and convert to fixed-point
        if self.z > 180:
            self.z -= 360
        if self.z < -180:
            self.z += 360
        if self.z == 180:
            self.z = -180
        self.z = int(self.z * (2**15 / 180))

    def friend_angles(self):
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
            Sx = (self.x << 4) - self.x # 15x = 16x - x
            Sy = (self.y << 4) - self.y # 15y = 16y - y

        if input_angle_negative: # rotate counterclockwise
            self.x = Cx + Sy
            self.y = Cy - Sx
            self.z += self.stage_2_rotations[rotation_idx]
        else: # rotate clockwise
            self.x = Cx - Sy
            self.y = Cy + Sx
            self.z -= self.stage_2_rotations[rotation_idx]
        # magnitude correction is used here
        self.x = self.x >> 4
        self.y = self.y >> 4

        """
        Here is a trial of reading the hardware architecture from the research paper. I realized that it is
        unnecessarily complex and does not add to the understanding of the algorithm.
        This will remain here for this for version control but will be scrapped.
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
    def friend_angles_optimized(self):
        # Optimized for using less additions and subtractions.
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

        if rotation_idx == 0: # P_0 = 25 + j0
            Cx = self.adder_subtractor(self.adder_subtractor((self.x << 4), (self.x << 3), subtract=False), self.x, subtract=False)
            #Cx = (self.x << 4) + (self.x << 3) + self.x # 25x = 16x + 8x + x
            #Cy = 0 25y = 0 on stage 2, since cordic is initialized with y = 0, we can skip the addition and subtraction for y in this case
            #Sx = 0
            #Sy = 0
            if input_angle_negative: # rotate counterclockwise
                self.x = Cx
                self.y = self.y # Cy - Sx but Cy and Sx are 0 on stage 2, since cordic is initialized with y = 0, we can skip the addition and subtraction for y in this case
                self.z += self.stage_2_rotations[rotation_idx]
            else: # rotate clockwise
                self.x = Cx
                self.y = self.y # Cy + Sx but Cy and Sx are 0 on stage 2, since cordic is initialized with y = 0, we can skip the addition and subtraction for y in this case
                self.z -= self.stage_2_rotations[rotation_idx]
        elif rotation_idx == 1: # P_1 = 24 + j7
            Cx = self.adder_subtractor((self.x << 4), (self.x << 3), subtract=False)
            Sx = self.adder_subtractor((self.x << 3), self.x, subtract=True)
            #Cx = (self.x << 4) + (self.x << 3) # 24x = 16x + 8x
            #Cy = 0 24y = 0 on stage 2, since cordic is initialized with y = 0, we can skip the addition and subtraction for y in this case
            #Sx = (self.x << 3) - self.x # 7x = 8x - x
            # Sy = 0 7y = 0 on stage 2, since cordic is initialized with y = 0, we can skip the addition and subtraction for y in this case
            if input_angle_negative: # rotate counterclockwise
                self.x = Cx # + Sy but Sy is 0
                self.y = -Sx # Cy - Sx but Cy is 0
                self.z += self.stage_2_rotations[rotation_idx]
            else: # rotate clockwise
                self.x = Cx # - Sy but Sy is 0
                self.y = Sx # Cy + Sx but Cy is 0
                self.z -= self.stage_2_rotations[rotation_idx]
        else: # P_2 = 20 + j15
            Cx = self.adder_subtractor((self.x << 4), (self.x << 2), subtract=False)
            Sx = self.adder_subtractor((self.x << 4), self.x, subtract=True)

            #Cx = (self.x << 4) + (self.x << 2) # 20x = 16x + 4x
            # Cy = 0: 20y = 0 on stage 2, since cordic is initialized with y = 0, we can skip the addition and subtraction for y in this case
            #Sx = (self.x << 4) - self.x # 15x = 16x - x
            # Sy = 0: 15y = 0 on stage 2, since cordic is initialized with y = 0, we can skip the addition and subtraction for y in this case
            if input_angle_negative: # rotate counterclockwise
                self.x = Cx # + Sy but Sy is 0
                self.y = -Sx # Cy - Sx but Cy is 0. Also in hardware flip bits and add one to get the negative value.
                self.z += self.stage_2_rotations[rotation_idx]
            else: # rotate clockwise
                self.x = Cx # - Sy but Sy is 0
                self.y = Sx # Cy + Sx but Cy is 0
                self.z -= self.stage_2_rotations[rotation_idx]

        # Magnitude correction
        self.x = self.x >> 4
        self.y = self.y >> 4
        # Optimized friend angle stage uses maximum of 3 additions/subtractions if rotation is not taken into account.

    def usr_cordic(self):
        # select rotation angle and sign based on input angle
        # options are:
        # P_0 = 129 + j0
        # P_1 = 128 + j16
        goal_max_magnitude = int(3.563 * (2**15 / 180))
        abs_z = abs(self.z)

        if abs_z <= goal_max_magnitude:
            rotation_idx = 0
        else:
            rotation_idx = 1
        input_angle_negative = self.z < 0

        if rotation_idx == 0:
            Cx = (self.x << 7) + self.x # 129x = 128x + x
            Cy = (self.y << 7) + self.y # 129y = 128y + y
            Sx = 0
            Sy = 0
        else:
            Cx = (self.x << 7) # 128x = 128x
            Cy = (self.y << 7) # 128y = 128y
            Sx = (self.x << 4) # 16x = 16x
            Sy = (self.y << 4) # 16y = 16y
        if input_angle_negative: # rotate counterclockwise
            self.x = Cx + Sy
            self.y = Cy - Sx
            self.z += self.stage_3_rotations[rotation_idx]
        else: # rotate clockwise
            self.x = Cx - Sy
            self.y = Cy + Sx
            self.z -= self.stage_3_rotations[rotation_idx]

        # magnite correction
        if rotation_idx == 0:
            self.x = self.x >> 7
            self.y = self.y >> 7
        else:
            self.x = self.x >> 7
            self.y = self.y >> 7

    def usr_cordic_optimized(self):
        # select rotation angle and sign based on input angle
        # options are:
        # P_0 = 129 + j0
        # P_1 = 128 + j16
        goal_max_magnitude = int(3.563 * (2**15 / 180))
        abs_z = abs(self.z)

        if abs_z <= goal_max_magnitude:
            rotation_idx = 0
        else:
            rotation_idx = 1
        input_angle_negative = self.z < 0

        if rotation_idx == 0:
            Cx = (self.x << 7) + self.x # 129x = 128x + x
            Cy = (self.y << 7) + self.y # 129y = 128y + y
            #Sx = 0
            #Sy = 0
            self.x = Cx
            self.y = Cy
            if input_angle_negative: # rotate counterclockwise
                self.x = Cx
                self.y = Cy
                self.z += self.stage_3_rotations[rotation_idx]
            else: # rotate clockwise
                self.x = Cx
                self.y = Cy
                self.z -= self.stage_3_rotations[rotation_idx]
        else:
            Cx = (self.x << 7) # 128x = 128x
            Cy = (self.y << 7) # 128y = 128y
            Sx = (self.x << 4) # 16x = 16x
            Sy = (self.y << 4) # 16y = 16y
            self.x = self.adder_subtractor(Cx, Sy, subtract=not input_angle_negative)
            self.y = self.adder_subtractor(Cy, Sx, subtract=input_angle_negative)
            if input_angle_negative: # rotate counterclockwise
                self.z += self.stage_3_rotations[rotation_idx]
            else: # rotate clockwise
                self.z -= self.stage_3_rotations[rotation_idx]

        # magnitude correction
        self.x = self.x >> 7
        self.y = self.y >> 7

    def nano_rotations(self):
        # stage 6: We find the best candidate of 9 iterations of nano rotations:
        # P_k = 512 + jk for k = 0, 1, ..., 8
        goal_max_magnitude = int(0.056 * (2**15 / 180))
        abs_z = abs(self.z)
        rotation_idx = 8
        # The following loop is to be unrolled in the chisel implementation
        for k in range(0, 9):
            if abs_z <= goal_max_magnitude + self.stage_6_rotations[k]:
                rotation_idx = k
                break
        input_angle_negative = self.z < 0
        angle = self.stage_6_rotations[rotation_idx]
        Cx = (self.x << 9)
        Cy = (self.y << 9)
        # Here we multiply
        Sx = self.x * rotation_idx
        Sy = self.y * rotation_idx
        if input_angle_negative: # rotate counterclockwise
            self.x = Cx + Sy
            self.y = Cy - Sx
            self.z += angle
        else: # rotate clockwise
            self.x = Cx - Sy
            self.y = Cy + Sx
            self.z -= angle
        # magnitude correction
        self.x = self.x >> 9
        self.y = self.y >> 9

    def adder_subtractor(self, a, b, subtract=False):
        if subtract:
            return a - b
        else:
            return a + b
    def run(self):
        self.trivial_rotations()
        self.friend_angles()
        self.usr_cordic()
        self.cordic(0)
        self.cordic(1)
        self.nano_rotations()