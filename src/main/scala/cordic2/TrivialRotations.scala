package Cordic2
import chisel3._
import chisel3.util._

class TrivialRotations(N: Int = 16) extends Module {
  val input = IO(Flipped(Valid(new Cordic2Payload(N))))
  val output = IO(Valid(new Cordic2Payload(N)))

  val deg_45      = (1 << (N-3)).S        // 45 degrees in fixed-point representation
  val deg_135     = (deg_45 * 3.S)        // 135 degrees in fixed-point representation
  val neg_deg_180 = (-1 << (N-1)).S       // -180 degrees in fixed-point representation
  val deg_180     = ((1 << (N-1)) - 1).S  // positive ~180 degrees in fixed-point representation
  val constants   = new Cordic2Constants(N)

  val busy        = RegInit(false.B)  // stage is processing
  val resultValid = RegInit(false.B)  // x/y computed, z not yet calculated
  val subtract    = RegInit(false.B)  // whether to subtract rot from z
  val rot         = RegInit(0.S(N.W)) // rotation amount to apply to z

  // Registered capture of input — stable across multi-cycle processing
  val xReg = RegInit(0.S(N.W))
  val yReg = RegInit(0.S(N.W))
  val zReg = RegInit(0.S(N.W))

  // Registered output bits — hold values until output.valid is asserted
  val outX = RegInit(0.S(N.W))
  val outY = RegInit(0.S(N.W))
  val outZ = RegInit(0.S(N.W))

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
  when (busy && !resultValid) {
    when (zReg < 0.S) {
      // Rotate clockwise
      when (-deg_135 <= zReg && zReg < -deg_45) {
        outX        := yReg
        outY        := -xReg
        rot         := constants.TRIVIAL_ROT(1).S
        subtract    := true.B
        resultValid := true.B
      } .elsewhen (neg_deg_180 <= zReg && zReg < -deg_135) {
        outX        := -xReg
        outY        := -yReg
        rot         := constants.TRIVIAL_ROT(2).S
        subtract    := true.B
        resultValid := true.B
      } .otherwise {
        // No rotation needed — skip adderSub entirely
        outX        := xReg
        outY        := yReg
        outZ        := zReg
        resultValid := true.B
        busy        := false.B
      }
    } .otherwise {
      // Rotate counterclockwise
      when (deg_45 < zReg && zReg <= deg_135) {
        outX        := -yReg
        outY        := xReg
        rot         := constants.TRIVIAL_ROT(1).S
        subtract    := false.B
        resultValid := true.B
      } .elsewhen (deg_135 < zReg && zReg <= deg_180) {
        outX        := -xReg
        outY        := -yReg
        rot         := constants.TRIVIAL_ROT(2).S
        subtract    := false.B
        resultValid := true.B
      } .otherwise {
        // No rotation needed — skip adderSub entirely
        outX        := xReg
        outY        := yReg
        outZ        := zReg
        resultValid := true.B
        busy        := false.B
      }
    }
  }

  // Condition 4: compute z and clear busy (only reached when adderSub is needed;
  // otherwise branches set busy=false so this block never fires for them)
  // NOTE: This block should be calculated in parallel with the stage calculations to save time, move it!
  when (busy && resultValid) {
    outZ := constants.adderSub(zReg, rot, subtract)
    busy := false.B
  }


  // Condition 5: assert output valid and clear resultValid so stage can accept new input
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