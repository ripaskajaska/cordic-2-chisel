package Cordic2
import chisel3._
import chisel3.util._

class TrivialRotations(N: Int = 16) extends Module {
  val input = IO(Flipped(Valid(new Cordic2Payload(N))))
  val output = IO(Valid(new Cordic2Payload(N)))

  val x = input.bits.x
  val y = input.bits.y
  val z = input.bits.z
  val deg_45 = (1 << (N-3)).S  // 45 degrees in fixed-point representation
  val deg_135 = (deg_45 * 3.S)  // 135 degrees in fixed-point representation
  val neg_deg_180 = (-1 << (N-1)).S  // -180 degrees in fixed-point representation
  val deg_180 = ((1 << (N-1)) - 1).S  // positive ~180 degrees in fixed-point representation
  val constants = new Cordic2Constants(N)
  val busy        = RegInit(false.B) // Registers to hold the output of the rotation while it's being processed. 
  val resultValid = RegInit(false.B) // keep track of whether the output registers hold a valid result for final output assignment
  val subtract    = RegInit(false.B) // keep track of whether the next rotation should subtract or add the angle
  val rot         = RegInit(0.S(N.W)) // angle index for rotation
  output.valid := RegInit(false.B)
  // Check if input is valid and make algorithm busy
  when (input.valid && !busy && !resultValid) {
    busy := true.B
  }
  when (busy && !resultValid) {
    when (z < 0.S) {
      // Rotate clockwise
      when (-deg_135 <= z && z < -deg_45) {
        output.bits.x := y
        output.bits.y := -x
        rot := constants.TRIVIAL_ROT(1).S
        resultValid := true.B
      } .elsewhen (neg_deg_180 <= z && z < -deg_135) {
        output.bits.x := -x
        output.bits.y := -y
        rot := constants.TRIVIAL_ROT(2).S
        resultValid := true.B
      } .otherwise {
        output.bits.x := x
        output.bits.y := y
        output.bits.z := z
        resultValid := true.B
        busy := false.B
      }
      subtract := true.B
    } .otherwise {
      // Rotate counterclockwise  
      when (deg_45 < z && z <= deg_135) {
        output.bits.x := -y
        output.bits.y := x
        rot := constants.TRIVIAL_ROT(1).S
        resultValid := true.B
      } .elsewhen (deg_135 < z && z <= deg_180) {
        output.bits.x := -x
        output.bits.y := -y
        rot := constants.TRIVIAL_ROT(2).S
        resultValid := true.B
      } .otherwise {
        output.bits.x := x
        output.bits.y := y
        output.bits.z := z
        resultValid := true.B
        busy := false.B
      }
      subtract := false.B
    }
  }
  when (busy && resultValid) {
    output.bits.z := constants.adderSub(z, rot, subtract)
    busy := false.B
  }
  when (!busy && resultValid) {
    output.valid := true.B
  }
}