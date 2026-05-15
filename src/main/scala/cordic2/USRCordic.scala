package Cordic2
import chisel3._
import chisel3.util._

class USRCordic(N: Int = 16) extends Module {
  val input = IO(Flipped(Valid(new Cordic2Payload(N))))
  val output = IO(Valid(new Cordic2Payload(N)))

  // USR cordic is a stage for performing the operations of either
  // P_0 = 129 + j0 or
  // P_1 = 128 + j16
  // on the x and y inputs

  
  val constants   = new Cordic2Constants(N)

  val busy        = RegInit(false.B)  // stage is processing
  val resultValid = RegInit(false.B)  // x/y computed, z not yet calculated
  val subtract    = RegInit(false.B)  // whether to subtract rot from z
  val rot         = RegInit(0.S(N.W)) // rotation amount to apply to z
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

  // USR cordic has maximum of 3 adders used. We declare them and their I/O wires here.
  // NOTE: Adder input/output wires use width inference (SInt() not SInt(N.W)).
  // This prevents truncation of intermediate values during shifts (e.g., xReg << 7 is 23 bits).
  // Chisel will infer the maximum width needed across all assignments to each wire.
  val add1_a   = Wire(SInt())
  val add1_b   = Wire(SInt())
  val add1_sub = Wire(Bool())
  val add1_out = constants.adderSub(add1_a, add1_b, add1_sub)

  val add2_a   = Wire(SInt())
  val add2_b   = Wire(SInt())
  val add2_sub = Wire(Bool())
  val add2_out = constants.adderSub(add2_a, add2_b, add2_sub)
  
  // Third adder for calculating z value
  val add3_a   = Wire(SInt())
  val add3_b   = Wire(SInt())
  val add3_sub = Wire(Bool())
  val add3_out = constants.adderSub(add3_a, add3_b, add3_sub)

  // Declare default values for adder inputs to avoid latching irrelevant values
  add1_a := DontCare; add1_b := DontCare; add1_sub := DontCare
  add2_a := DontCare; add2_b := DontCare; add2_sub := DontCare
  add3_a := DontCare; add3_b := DontCare; add3_sub := DontCare
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
  // Condition 3 determine which rotation we're doing based on zReg
  when (busy && !rotSet) {
    val absZ = constants.abs(zReg)
    when (absZ <= constants.USR_GOAL_MAX.S) {
      rotIdx := 0.U
      rot := constants.USR_ROT(0).S
    } .otherwise {
      rotIdx := 1.U
      rot := constants.USR_ROT(1).S
    }

    when(zReg < 0.S) {
      subtract := false.B
    } .otherwise {
      subtract := true.B
    }
    subtractValid := true.B
    rotSet := true.B
  }
  // Condition 4: main functionality — only when busy and result not yet ready
  when(busy && !resultValid && rotSet) {
    
    when (rotIdx === 0.U) {
      // P_0 = 129 + j0  (Sx=0, Sy=0 always)
      // 128x + x = 129x
      add1_a := xReg << 7
      add1_b := xReg
      add1_sub := false.B 
      outX := add1_out >> 7
      // 128y + y = 129y
      add2_a := yReg << 7
      add2_b := yReg
      add2_sub := false.B
      outY := add2_out >> 7
      resultValid := true.B
      busy        := false.B
    } .otherwise {
      //x = Cx + Sy, when z is negative, else x = Cx - Sy
      //y = Cy - Sx, when z is negative, else y = Cy + Sx
      // P_1 = 128 + j16
      // 128x + j16y
      add1_a := xReg << 7 // Cx
      add1_b := yReg << 4 // Sy
      add1_sub := subtract
      outX := add1_out >> 7
      // 128y + j16x
      add2_a := yReg << 7 // Cy
      add2_b := xReg << 4 // Sx
      add2_sub := !subtract
      outY := add2_out >> 7
      resultValid := true.B
      busy        := false.B
    }
  }
   // Condition 5: compute z, happens in parallel with condition 4 but only after we've set the subtract signal
  when (subtractValid && busy && !zCalcDone) {
    add3_a := zReg
    add3_b := rot
    add3_sub := subtract
    outZ := add3_out
    subtractValid := false.B
    zCalcDone := true.B
  }

  // Condition 6: assert output valid and clear resultValid so stage can accept new input
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
    rotSet := false.B
    subtract := false.B
    zCalcDone := false.B
  }
}