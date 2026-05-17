package Cordic2
import chisel3._
import chisel3.util._

class NanoRotations(N: Int = 16) extends Module {
  val input = IO(Flipped(Valid(new Cordic2Payload(N))))
  val output = IO(Valid(new Cordic2Payload(N)))

  val constants   = new Cordic2Constants(N)

  val busy        = RegInit(false.B)  // stage is processing
  val resultValid = RegInit(false.B)  // x/y computed, z not yet calculated
  val subtract    = RegInit(false.B)  // whether to subtract rot from z
  val rot         = RegInit(0.S(N.W)) // rotation amount to apply to z
  val rotIdx      = RegInit(0.U(4.W)) 
  val rotSet = RegInit(false.B) // register to track whether we've set the rot value for the current operation

  // Registered capture of input — stable across multi-cycle processing
  val xReg = RegInit(0.S(N.W))
  val yReg = RegInit(0.S(N.W))
  val zReg = RegInit(0.S(N.W))

  // Registered output bits — hold values until output.valid is asserted
  val outX = RegInit(0.S(N.W))
  val outY = RegInit(0.S(N.W))
  val outZ = RegInit(0.S(N.W))

  // NanoRotations has maximum of 5 adders used. We declare them and their I/O wires here.
  // NOTE: Adder input/output wires use width inference (SInt() not SInt(N.W)).
  // This prevents truncation of intermediate values during shifts (e.g., xReg << 9 is 25 bits).
  // Chisel will infer the maximum width needed across all assignments to each wire.
  val add1_a   = Wire(SInt())
  val add1_b   = Wire(SInt())
  val add1_sub = Wire(Bool())
  val add1_out = constants.adderSub(add1_a, add1_b, add1_sub)

  val add2_a   = Wire(SInt())
  val add2_b   = Wire(SInt())
  val add2_sub = Wire(Bool())
  val add2_out = constants.adderSub(add2_a, add2_b, add2_sub)
  
  // NOTE: Adder input/output wires use width inference (SInt() not SInt(N.W)).
  // This prevents truncation of intermediate values during shifts (e.g., xReg << 9 is 25 bits).
  // Chisel will infer the maximum width needed across all assignments to each wire.
  val add3_a   = Wire(SInt())
  val add3_b   = Wire(SInt())
  val add3_sub = Wire(Bool())
  val add3_out = constants.adderSub(add3_a, add3_b, add3_sub)

  val add4_a   = Wire(SInt())
  val add4_b   = Wire(SInt())
  val add4_sub = Wire(Bool())
  val add4_out = constants.adderSub(add4_a, add4_b, add4_sub)
  
  // Fifth adder for calculating z value
  val add5_a   = Wire(SInt())
  val add5_b   = Wire(SInt())
  val add5_sub = Wire(Bool())
  val add5_out = constants.adderSub(add5_a, add5_b, add5_sub)
  // Declare default values for adder inputs to avoid latching irrelevant values
  add1_a := DontCare; add1_b := DontCare; add1_sub := DontCare
  add2_a := DontCare; add2_b := DontCare; add2_sub := DontCare
  add3_a := DontCare; add3_b := DontCare; add3_sub := DontCare
  add4_a := DontCare; add4_b := DontCare; add4_sub := DontCare
  add5_a := DontCare; add5_b := DontCare; add5_sub := DontCare

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
    val rotIndx = MuxCase(8.U,
    (0 until 9).map { k =>
      (absZ <= (constants.NANO_GOAL_MAX.S + constants.NANO_ROTS(k).S(N.W))) -> k.U
    })
    rotIdx := rotIndx
    rot := MuxLookup(rotIndx, constants.NANO_ROTS(8).S(N.W), (0 until 9).map(k => k.U -> constants.NANO_ROTS(k).S(N.W)))
    when(zReg < 0.S) {
      subtract := false.B
    } .otherwise {
      subtract := true.B
    }
    rotSet := true.B
  }
  // We choose the rotation index based on which predefined nano rotation brings us closest to the goal
 
  // Condition 4: main functionality — only when busy and result not yet ready
  when(busy && !resultValid && rotSet) {
    // We implement the multiplier for the x and y rotations using a sequence of shifts and adds based on the selected rotation index
    // We also complete the total summation
    when (rotIdx === 0.U) { // 0: Sx and Sy = 0
      // We skip adders entirely
      outX := (xReg << 9) >> 9
      outY := (yReg << 9) >> 9
      outZ := zReg
      resultValid := true.B
      busy := false.B

    } .elsewhen(rotIdx === 1.U) { // 1: x*1 = x, y*1 = y
      // We skip multiplication entirely since it's just x and y
      add1_a   := xReg << 9
      add1_b   := yReg
      add1_sub := subtract
      outX := add1_out >> 9
      add2_a   := yReg << 9
      add2_b   := xReg
      add2_sub := !subtract
      outY := add2_out >> 9
      add5_a   := zReg
      add5_b   := rot
      add5_sub := subtract
      outZ := add5_out
      resultValid := true.B
      busy := false.B
    } .elsewhen(rotIdx === 2.U) { // 2: x*2 = x << 1, y*2 = y << 1
      add1_a   := xReg << 9
      add1_b   := yReg << 1
      add1_sub := subtract
      outX := add1_out >> 9
      add2_a   := yReg << 9
      add2_b   := xReg << 1
      add2_sub := !subtract
      outY := add2_out >> 9
      add5_a   := zReg
      add5_b   := rot
      add5_sub := subtract
      outZ := add5_out
      resultValid := true.B
      busy := false.B
    } .elsewhen(rotIdx === 3.U) { // 3: X*3 = (x << 1) + x, Y*3 = (y << 1) + y
      add1_a   := yReg << 1; add1_b   := yReg; add1_sub := false.B
      add2_a := xReg << 9; add2_b := add1_out; add2_sub := subtract
      outX := add2_out >> 9
      add3_a   := xReg << 1; add3_b   := xReg; add3_sub := false.B
      add4_a := yReg << 9; add4_b := add3_out; add4_sub := !subtract
      outY := add4_out >> 9
      add5_a   := zReg; add5_b   := rot; add5_sub := subtract
      outZ := add5_out
      resultValid := true.B
      busy := false.B
    } .elsewhen(rotIdx === 4.U) { // 4: X*4 = x << 2, Y*4 = y << 2
      add1_a   := xReg << 9
      add1_b   := yReg << 2
      add1_sub := subtract
      outX := add1_out >> 9
      add2_a   := yReg << 9
      add2_b   := xReg << 2
      add2_sub := !subtract
      outY := add2_out >> 9
      add5_a   := zReg
      add5_b   := rot
      add5_sub := subtract
      outZ := add5_out
      resultValid := true.B
      busy := false.B
    } .elsewhen(rotIdx === 5.U) { // 5: X*5 = (x << 2) + x, Y*5 = (y << 2) + y
      add1_a   := yReg << 2; add1_b   := yReg; add1_sub := false.B
      add2_a := xReg << 9; add2_b := add1_out; add2_sub := subtract
      outX := add2_out >> 9
      add3_a   := xReg << 2; add3_b   := xReg; add3_sub := false.B
      add4_a := yReg << 9; add4_b := add3_out; add4_sub := !subtract
      outY := add4_out >> 9
      add5_a   := zReg; add5_b   := rot; add5_sub := subtract
      outZ := add5_out
      resultValid := true.B
      busy := false.B
    } .elsewhen(rotIdx === 6.U) { // 6: X*6 = (x << 2) + (x << 1), Y*6 = (y << 2) + (y << 1)
      add1_a   := yReg << 2; add1_b   := yReg << 1; add1_sub := false.B
      add2_a := xReg << 9; add2_b := add1_out; add2_sub := subtract
      outX := add2_out >> 9
      add3_a   := xReg << 2; add3_b   := xReg << 1; add3_sub := false.B
      add4_a := yReg << 9; add4_b := add3_out; add4_sub := !subtract
      outY := add4_out >> 9
      add5_a   := zReg; add5_b   := rot; add5_sub := subtract
      outZ := add5_out
      resultValid := true.B
      busy := false.B
    } .elsewhen(rotIdx === 7.U) { // 7: X*7 = (x << 3) - x + x, Y*7 = (y << 3) - y
      add1_a   := yReg << 3; add1_b   := yReg; add1_sub := true.B
      add2_a := xReg << 9; add2_b := add1_out; add2_sub := subtract
      outX := add2_out >> 9
      add3_a   := xReg << 3; add3_b   := xReg; add3_sub := true.B
      add4_a := yReg << 9; add4_b := add3_out; add4_sub := !subtract
      outY := add4_out >> 9
      add5_a   := zReg; add5_b   := rot; add5_sub := subtract
      outZ := add5_out
      resultValid := true.B
      busy := false.B
    } .otherwise { // rotIdx === 8.U, 8: X*8 = x << 3, Y*8 = y << 3
      add1_a   := xReg << 9
      add1_b   := yReg << 3
      add1_sub := subtract
      outX := add1_out >> 9
      add2_a   := yReg << 9
      add2_b   := xReg << 3
      add2_sub := !subtract
      outY := add2_out >> 9
      add5_a   := zReg
      add5_b   := rot
      add5_sub := subtract
      outZ := add5_out
      resultValid := true.B
      busy := false.B
    }
  }
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
  }

}