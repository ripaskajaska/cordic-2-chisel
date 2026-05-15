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
  val subtractValid = RegInit(false.B) // register to track whether we've set the subtract signal for the current operation
  val zCalcDone = RegInit(false.B) // register to track whether we've calculated the new z value for the current operation
  val rotSet = RegInit(false.B) // register to track whether we've set the rot value for the current operation

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

  // Default combinational output
  output.valid  := false.B
  output.bits.x := outX >> 4
  output.bits.y := outY >> 4
  output.bits.z := outZ

  // Condition 1 & 2: capture input into registers and set busy
  when (input.valid && !busy && !resultValid) {
    busy := true.B
    xReg := input.bits.x
    yReg := input.bits.y
    zReg := input.bits.z
  }
  // Condition 3 determine which rotation we're doing based on zReg 
  when (busy && !rotSet) {
    val absZ = constants.abs(zReg)
    when (absZ <= constants.FRIEND_GOAL_MAX.S) {
      rotIdx := 0.U
      rot := constants.FRIEND_ROT(0).S
    } .elsewhen (absZ <= (constants.FRIEND_ROT(1) + constants.FRIEND_GOAL_MAX).S) {
      rotIdx := 1.U
      rot := constants.FRIEND_ROT(1).S
    } .otherwise {
      rotIdx := 2.U
      rot := constants.FRIEND_ROT(2).S
    }
    rotSet := true.B
  }
  // Condition 4: main functionality — only when busy and result not yet ready
  when(busy && !resultValid && rotSet) {
    when(zReg < 0.S && !subtractValid && !zCalcDone) {
      subtract := false.B
      subtractValid := true.B
    } .otherwise {
      subtract := true.B
      subtractValid := true.B
    }
    
    val yIsZero = (yReg === 0.S)
    val xIsZero = (xReg === 0.S)
    // In friend angles, either x or y is zero, since y starts at zero
    when (rotIdx === 0.U) {
      when (yIsZero) { // y = 0
        when (calcStage === 0.U) {
          // Calculate first addition for Cx: 16x+8x
          // self.adder_subtractor(self.x << 4, self.x << 3, False)
          add1_a   := xReg << 4
          add1_b   := xReg << 3
          add1_sub := false.B
          calcStage := 1.U
          // next clock calculate second addition for Cx
        } .elsewhen(calcStage === 1.U) {
          // Calculate second addition for Cx: Cx + x -> 16x+8x+x
          add2_a   := add1_out
          add2_b   := xReg
          add2_sub := false.B
          outX := add2_out
          outY := yReg
          resultValid := true.B
          busy := false.B
          calcStage := 0.U
        }
      } .elsewhen(xIsZero) { // x = 0
        when (calcStage === 0.U) {
          // Calculate first addition for Cy: 16y+8y
          add1_a   := yReg << 4
          add1_b   := yReg << 3
          add1_sub := false.B
          busy := false.B
          calcStage := 1.U
          // next clock: calculate second addition for Cy
        } .elsewhen(calcStage === 1.U) {
          // Calculate second addition for Cy: Cy + y -> 16y+8y+y
          add2_a   := add1_out
          add2_b   := yReg
          add2_sub := false.B
          outX := xReg
          outY := add2_out
          resultValid := true.B
          busy := false.B
          calcStage := 0.U
        }
      }
    } .elsewhen (rotIdx === 1.U) {
      when (yIsZero) { // y = 0
        // 16x+8x -> Cx
        add1_a   := xReg << 4
        add1_b   := xReg << 3
        add1_sub := false.B
        // 8x-x -> Sx
        add2_a   := xReg << 3
        add2_b   := xReg
        add2_sub := true.B
        outX := add1_out
        outY := Mux(!subtract, -add2_out, add2_out)
        resultValid := true.B
        busy := false.B
      } .elsewhen (xIsZero) { // x = 0
        // 16y+8y -> Cy
        add1_a   := yReg << 4
        add1_b   := yReg << 3
        add1_sub := false.B
        // 8y-y -> Sy
        add2_a   := yReg << 3
        add2_b   := yReg
        add2_sub := true.B
        outX := Mux(!subtract, add2_out, -add2_out)
        outY := add1_out
        resultValid := true.B
        busy := false.B
      }
    } .elsewhen(rotIdx === 2.U) {
      // Cx = 0 if x = 0 else 16x+4x
      // Sx = 0 if x = 0 else 16x-x
      // Cy = 0 if y = 0 else 16y+4y
      // Sy = 0 if y = 0 else 16y-y
      when (yIsZero) { // y = 0
        // Cx: 16x+4x
        add1_a   := xReg << 4
        add1_b   := xReg << 2
        add1_sub := false.B
        // Sx: 16x-x
        add2_a   := xReg << 4
        add2_b   := xReg
        add2_sub := true.B
        outX := add1_out
        outY := Mux(!subtract, -add2_out, add2_out)
        resultValid := true.B
        busy := false.B
      } .elsewhen (xIsZero) { // x = 0
        // Cy: 16y+4y
        add1_a   := yReg << 4
        add1_b   := yReg << 2
        add1_sub := false.B
        // Sy: 16y-y
        add2_a   := yReg << 4
        add2_b   := yReg
        add2_sub := true.B
        outX := Mux(!subtract, add2_out, -add2_out)
        outY := add1_out
        resultValid := true.B
        busy := false.B
      }
    }
  }
  // Condition 5: compute z, happens in parallel with condition 4 but only after we've set the subtract signal
  when (subtractValid && busy && !zCalcDone) {
    outZ := constants.adderSub(zReg, rot, subtract)
    subtractValid := false.B
    zCalcDone := true.B
  }
  // Condition 6: assert output valid and clear resultValid so stage can accept new input
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
    zCalcDone := false.B
  }
}