package Cordic2
import chisel3._
import chisel3.util._

class FriendAngles(N: Int = 16) extends Module {
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
  val absZ        = RegInit(0.S(N.W))
  output.valid := RegInit(false.B)
  when (input.valid && !busy && !resultValid) {
    busy := true.B
    absZ := constants.abs(z)
    when (z < 0.S) {
      // Rotate clockwise
      
      subtract := true.B
    } .otherwise {
      // Rotate counterclockwise  
      
    }
    when (busy && resultValid) {
      output.bits.z := constants.adderSub(z, rot, subtract)
      busy := false.B
    }
    when (!busy && resultValid) {
      output.valid := true.B
    }
  }
  when(input.valid) {
    when (absZ <= constants.FRIEND_GOAL_MAX.S) {
      val rotIdx = 0
      val rot = constants.FRIEND_ROT(0)
    } .elsewhen (absZ <= (constants.FRIEND_ROT(1) + constants.FRIEND_GOAL_MAX).S) {
      val rotIdx = 1
      val rot = constants.FRIEND_ROT(1)
    } .otherwise {
      val rotIdx = 2
      val rot = constants.FRIEND_ROT(2)
    }

    
  }
}