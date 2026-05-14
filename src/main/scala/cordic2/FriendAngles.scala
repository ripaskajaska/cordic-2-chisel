package Cordic2
import chisel3._
import chisel3.util._

class FriendAngles(N: Int = 16) extends Module {
 val input = IO(Flipped(Valid(new Cordic2Payload(N))))
  val output = IO(Valid(new Cordic2Payload(N)))

  val constants   = new Cordic2Constants(N)

  val busy        = RegInit(false.B)  // stage is processing
  val resultValid = RegInit(false.B)  // x/y computed, z not yet calculated
  val subtract    = RegInit(false.B)  // whether to subtract rot from z
  val rot         = RegInit(0.S(N.W)) // rotation amount to apply to z
  val calcStage   = RegInit(0.U(2.W)) // Keep track of which adder/subtractor stage we're in 
  val rotIdx      = RegInit(0.U(2.W)) 
  // Registered capture of input — stable across multi-cycle processing
  val xReg = RegInit(0.S(N.W))
  val yReg = RegInit(0.S(N.W))
  val zReg = RegInit(0.S(N.W))

  // Registered output bits — hold values until output.valid is asserted
  val outX = RegInit(0.S(N.W))
  val outY = RegInit(0.S(N.W))
  val outZ = RegInit(0.S(N.W))

  // Friend angles has maximum of 5 adders used. We declare them and their I/O wires here
  val add1_a   = Wire(SInt(N.W))
  val add1_b   = Wire(SInt(N.W))
  val add1_sub = Wire(Bool())
  val add1_out = constants.adderSub(add1_a, add1_b, add1_sub)

  val add2_a   = Wire(SInt(N.W))
  val add2_b   = Wire(SInt(N.W))
  val add2_sub = Wire(Bool())
  val add2_out = constants.adderSub(add2_a, add2_b, add2_sub)

  val add3_a   = Wire(SInt(N.W))
  val add3_b   = Wire(SInt(N.W))
  val add3_sub = Wire(Bool())
  val add3_out = constants.adderSub(add3_a, add3_b, add3_sub)

  val add4_a   = Wire(SInt(N.W))
  val add4_b   = Wire(SInt(N.W))
  val add4_sub = Wire(Bool())
  val add4_out = constants.adderSub(add4_a, add4_b, add4_sub)

  val add5_a   = Wire(SInt(N.W))
  val add5_b   = Wire(SInt(N.W))
  val add5_sub = Wire(Bool())
  val add5_out = constants.adderSub(add5_a, add5_b, add5_sub)

  // Default combinational output
  output.valid  := false.B
  output.bits.x := outX
  output.bits.y := outY
  output.bits.z := outZ

  // Condition 1 & 2: capture input into registers and set busy
  when (input.valid && !busy && !resultValid) {
    busy := true.B
    xReg := input.bits.x
    yReg := input.bits.y
    zReg := input.bits.z
  }

  // Condition 3: main functionality — only when busy and result not yet ready
  when(busy && !resultValid) {
    val absZ = constants.abs(zReg)
    when (absZ <= constants.FRIEND_GOAL_MAX.S) {
      rotIdx := 0.U
      rot := constants.FRIEND_ROT(0).U
    } .elsewhen (absZ <= (constants.FRIEND_ROT(1) + constants.FRIEND_GOAL_MAX).S) {
      rotIdx := 1.U
      rot := constants.FRIEND_ROT(1).U
    } .otherwise {
      rotIdx := 2.U
      rot := constants.FRIEND_ROT(2).U
    }
    val yIsZero = (yReg === 0.S)
    val xIsZero = (xReg === 0.S)
    
    when (rotIdx === 0.U) {
      when (yIsZero) {
        when (calcStage === 0.U) {
          // self.adder_subtractor(self.x << 4, self.x << 3, False)
          add1_a   := xReg << 4
          add1_b   := xReg << 3
          add1_sub := false.B
          calcStage := 1.U
        } .elsewhen(calcStage === 1.U) {
          // self.adder_subtractor(add1_out, self.x, False)
          add2_a   := add1_out
          add2_b   := xReg
          add2_sub := false.B
          calcStage := 2.U 
        } .elsewhen(calcStage === 2.U) {
          outX := add2_out
          outY := yReg
          resultValid := true.B
        }
      } .elsewhen(xIsZero) {
        when (calcStage === 0.U) {
          // self.adder_subtractor(self.y << 4, self.y << 3, False)
          add1_a   := yReg << 4
          add1_b   := yReg << 3
          add1_sub := false.B
          calcStage := 1.U
        } .elsewhen(calcStage === 1.U) {
          // self.adder_subtractor(add1_out, self.y, False)
          add2_a   := add1_out
          add2_b   := yReg
          add2_sub := false.B
          calcStage := 2.U
        }.elsewhen(calcStage === 2.U) {
          outX := xReg
          outY := add2_out
          resultValid := true.B
          calcStage := 0.U
          when(zReg < 0.S) {
            subtract := false.B
          } .otherwise {
            subtract := true.B
          }
        }
      }
    } .elsewhen (rotIdx === 1.U) {
      when (yIsZero) {
        when (calcStage === 0.U) {
          // 16x+8x -> Cx
          add1_a   := xReg << 4
          add1_b   := xReg << 3
          add1_sub := false.B
          // 8x-x -> Sx
          add2_a   := xReg << 3
          add2_b   := xReg
          add2_sub := true.B
          calcStage := 1.U
        } .elsewhen(calcStage === 1.U) {
          when(zReg < 0.S) {
            // Cx + Sy -> outX, y=0 => outX = Cx
            outX := add1_out
            // Cy - Sx -> outY = -Sx
            outY := -add2_out
          } .otherwise {
            subtract := true.B
            // Cx - Sy, outY = 0 => outX = Cx
            outX := add1_out
            // Cy + Sx -> outY = Sx
            outY := add2_out
          }
          resultValid := true.B
          calcStage := 0.U
        }
      } .elsewhen (xIsZero) {
        when (calcStage === 0.U) {
          // 16y+8y -> Cy
          add1_a   := yReg << 4
          add1_b   := yReg << 3
          add1_sub := false.B
          // 8y-y -> Sy
          add2_a   := yReg << 3
          add2_b   := yReg
          add2_sub := true.B
          calcStage := 1.U
        } .elsewhen(calcStage === 1.U) {
          when(zReg < 0.S) {
            // Cx + Sy -> outX, x=0 => outX = Sy
            outX := add2_out
            // Cy - Sx -> outY = Cy
            outY := add1_out
          } .otherwise {
            subtract := true.B
            // Cx - Sy, x=0 => outX = -Sy
            outX := -add2_out
            // Cy + Sx -> outY = Cy
            outY := add1_out
          }
          resultValid := true.B
          calcStage := 0.U
        }
      }
    } .elsewhen(rotIdx === 2.U) {
      // Cx = 0 if x = 0 else 16x+4x
      // Sx = 0 if x = 0 else 16x-x
      // Cy = 0 if y = 0 else 16y+4y
      // Sy = 0 if y = 0 else 16y-y
      when (yIsZero) {
        when (calcStage === 0.U) {
          // Cx: 16x+4x
          add1_a   := xReg << 4
          add1_b   := xReg << 2
          add1_sub := false.B
          // Sx: 16x-x
          add2_a   := xReg << 4
          add2_b   := xReg
          add2_sub := true.B
          calcStage := 1.U
        } .elsewhen(calcStage === 1.U) {
          // y is zero
          when(zReg < 0.S) {
            // Cx + Sy -> outX, y=0 => outX = Cx
            outX := add1_out
            // Cy - Sx -> outY = -Sx
            outY := -add2_out
          } .otherwise {
            subtract := true.B
            // Cx - Sy, y=0 => outX = Cx
            outX := add1_out
            // Cy + Sx -> outY = Sx
            outY := add2_out
          }
          resultValid := true.B
          calcStage := 0.U
        }
      } .elsewhen (xIsZero) {
        when (calcStage === 0.U) {
          // Cy: 16y+4y
          add1_a   := yReg << 4
          add1_b   := yReg << 2
          add1_sub := false.B
          // Sy: 16y-y
          add2_a   := yReg << 4
          add2_b   := yReg
          add2_sub := true.B
          calcStage := 1.U
        } .elsewhen(calcStage === 1.U) {
          // x is zero
          when(zReg < 0.S) {
            // Cx + Sy -> outX, x=0 => outX = Sy
            outX := add2_out
            // Cy - Sx -> outY = Cy
            outY := add1_out
          } .otherwise {
            subtract := true.B
            // Cx - Sy, x=0 => outX = -Sy
            outX := -add2_out
            // Cy + Sx -> outY = Cy
            outY := add1_out
          }
          resultValid := true.B
          calcStage := 0.U
        }
      }
    }
  }

  // Condition 4: compute z and clear busy (only reached when adderSub is needed;
  // otherwise branches set busy=false so this block never fires for them)
  // TODO: This block should be calculated in parallel with the stage calculations to save time, move it!
  when (busy && resultValid) {
    outZ := constants.adderSub(zReg, rot, subtract)
    busy := false.B
  }

  // Condition 5: assert output valid and clear resultValid so stage can accept new input
 // TODO: update
  when (!busy && resultValid) {
    output.valid := true.B
    resultValid  := false.B
    xReg := 0.S
    yReg := 0.S
    zReg := 0.S
    outX := 0.S
    outY := 0.S
    outZ := 0.S
    rot      := 0.S
    subtract := false.B
  }

  
}