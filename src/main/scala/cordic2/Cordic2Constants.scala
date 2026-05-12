package Cordic2
import chisel3._
import chisel3.util._

object Cordic2Constants {
  def apply(N: Int): Cordic2Constants = new Cordic2Constants(N)
}
class Cordic2Constants(val N: Int = 16) {
  // Convert degrees to fixed-point representation (Q1.(N-1))
  private def degToFp(deg: Double): Int = (deg * (1 << (N-1)) / 180.0).toInt

  // Stage 1 thresholds
  val TRIVIAL_ROT = Seq(0, 1 << (N-2), (-(1 << (N-1)))) // 0, 2^14, -2^15

  // Stage 2: friend_angles
  val FRIEND_ROT      = Seq(0, degToFp(16.260), degToFp(36.870))
  val FRIEND_GOAL_MAX = degToFp(10.305)

  // Stage 3: usr_cordic
  val USR_ROT      = Seq(0, degToFp(7.125))
  val USR_GOAL_MAX = degToFp(3.563)

  // Stages 4 & 5: cordic micro-rotations
  val CORDIC_ANGLES = Seq(degToFp(1.790), degToFp(0.895))

  // Stage 6: nano_rotations
  val NANO_STEP     = degToFp(0.112)
  val NANO_GOAL_MAX = degToFp(0.056)
  val NANO_ROTS     = (0 to 8).map(_ * NANO_STEP)


  def adderSub(a: SInt, b: SInt, subtract: Bool): SInt =
    Mux(subtract, a - b, a + b)

  def abs(x: SInt): SInt = Mux(x < 0.S, -x, x)
}
