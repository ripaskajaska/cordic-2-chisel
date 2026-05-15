package Cordic2
import chisel3._
import chisel3.util._

class Cordic(N: Int = 16) extends Module {
  val input = IO(Flipped(Valid(new Cordic2Payload(N))))
  val output = IO(Valid(new Cordic2Payload(N)))

  // In CORDIC stage we run two consecutive rotations 
  // P_1 = 32 + j or P_1 64 + j 

  val constants   = new Cordic2Constants(N)

  val busy        = RegInit(false.B)  // stage is processing
  val resultValid = RegInit(false.B)  // x/y computed, z not yet calculated
  val subtract    = RegInit(false.B)  // whether to subtract rot from z
  val rot         = RegInit(0.S(N.W)) // rotation amount to apply to z
  val subtractValid = RegInit(false.B) // register to track whether we've set the subtract signal for the current operation
  val zCalcDone = RegInit(false.B) // register to track whether we've calculated the new z value for the current operation
  val rotSet = RegInit(false.B) // register to track whether we've set the rot value for the current operation
  val calculate = RegInit(false.B) // register to trigger algorithm
  val iteration = RegInit(0.U(1.W)) // register to track which iteration we're on (0 or 1)

  // Registered capture of input — stable across multi-cycle processing
  val xReg = RegInit(0.S(N.W))
  val yReg = RegInit(0.S(N.W))
  val zReg = RegInit(0.S(N.W))

  // Registered output bits — hold values until output.valid is asserted
  val outX = RegInit(0.S(N.W))
  val outY = RegInit(0.S(N.W))
  val outZ = RegInit(0.S(N.W))

  // USR cordic has maximum of 2 adders used. We declare them and their I/O wires here
  // NOTE: Adder input/output wires use width inference (SInt() not SInt(N.W)).
  // This prevents truncation of intermediate values during shifts (e.g., xReg << 6 is 22 bits).
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
    when (iteration === 0.U) {
      rot := constants.CORDIC_ANGLES(0).S
    } .elsewhen (iteration === 1.U) {
      rot := constants.CORDIC_ANGLES(1).S
    }
    when(zReg < 0.S) {
      subtract := false.B
    } .otherwise {
      subtract := true.B
    }
    subtractValid := true.B
    rotSet := true.B
    zCalcDone := false.B
    calculate := true.B
  }

  when (busy && !resultValid && rotSet && calculate) {

    when (iteration === 0.U) {
      // P_1 = 32 + j
      //x = Cx + Sy, when z is negative, else x = Cx - Sy
      //y = Cy - Sx, when z is negative, else y = Cy + Sx
      add1_a := xReg << 5
      add1_b := yReg
      add1_sub := subtract
      xReg := add1_out

      add2_a := yReg << 5
      add2_b := xReg
      add2_sub := !subtract
      yReg := add2_out
      calculate := false.B
      iteration := 1.U
      rotSet := false.B
    } .elsewhen(iteration === 1.U) {
      // P_1 = 64 + j
      add1_a := (xReg >> 5) << 6
      add1_b := (yReg >> 5)
      add1_sub := subtract
      outX := add1_out >> 6

      add2_a := (yReg >> 5) << 6
      add2_b := (xReg >> 5)
      add2_sub := !subtract
      outY := add2_out >> 6
      resultValid := true.B
      iteration := 0.U
      rotSet := false.B
      calculate := false.B
      busy := false.B
    }
  }
  // Condition 5: compute z, happens in parallel with condition 4 but only after we've set the subtract signal
  when (subtractValid && busy && !zCalcDone) {
    add3_a := zReg
    add3_b := rot
    add3_sub := subtract
    when (iteration === 0.U) {
      zReg := add3_out
    } .elsewhen (iteration === 1.U) {
      outZ := add3_out
    }
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
    subtract := false.B
    zCalcDone := false.B
    rotSet := false.B
  }
}