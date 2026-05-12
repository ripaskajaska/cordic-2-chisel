import chisel3._
import chisel3.util._
import cordic2._

class FriendAngles(N: Int = 16) extends Module {
  val input = IO(Flipped(Valid(new Cordic2Input(N))))
  val output = IO(Valid(new Cordic2Payload(N)))

  val absZ = abs(input.bits.z)

  when(input.valid) {
    when (absZ <= FRIEND_GOAL_MAX.S) {
      val rotIdx = 0.U
      val rot = FRIEND_ROT[rotIdx]
    } .elsewhen (absZ <= (FRIEND_ROT[1].S + FRIEND_GOAL_MAX.S)) {
      val rotIdx = 1.U
      val rot = FRIEND_ROT[rotIdx]
    } .otherwise {
      val rotIdx = 2.U
      val rot = FRIEND_ROT[rotIdx]
    }

    
  }
}