package Cordic2
import chisel3._
import chisel3.util._

class TrivialRotations(N: Int = 16) extends Module {
  val input = IO(Flipped(Valid(new Cordic2Input(N))))
  val output = IO(Valid(new Cordic2Payload(N)))

  when (input.valid) {
    val x = input.bits.x
    val y = input.bits.y
    val z = input.bits.z
    val deg_45 = 2^(N-3).S  // 45 degrees in fixed-point representation
    val deg_135 = deg_45 * 3  // 135 degrees in fixed-point representation
    val deg_180 = 2^(N-1).S  // 180 degrees in fixed-point representation
    
    when (z < 0.S) {
      // Rotate clockwise
      when ((-deg_135).S <= z && z < (-deg_45).S) {
        output.bits.x := y
        output.bits.y := -x
        output.bits.z := 0.S
        output.valid := true.B
      } .elsewhen (-deg_180.S <= z && z < (-deg_135).S) {
        output.bits.x := -x
        output.bits.y := -y
        output.bits.z := 0.S
        output.valid := true.B
      } .otherwise {
        output.bits.x := x
        output.bits.y := y
        output.bits.z := z
        output.valid := true.B
      }
      
    } .otherwise {
      // Rotate counterclockwise
      when ((deg_45).S < z && z <= (deg_135).S) {
        output.bits.x := -y
        output.bits.y := x
        output.bits.z := 0.S
        output.valid := true.B
      } .elsewhen (deg_135.S < z && z <= deg_180.S) {
        output.bits.x := -x
        output.bits.y := -y
        output.bits.z := 0.S
        output.valid := true.B
      } .otherwise {
        output.bits.x := x
        output.bits.y := y
        output.bits.z := z
        output.valid := true.B
      }
    }
  }
}